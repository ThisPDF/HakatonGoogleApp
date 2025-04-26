/**
 * Smart Home Controller for ESP32 Sparrow Rev 2 running NuttX
 */

#include <nuttx/config.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <debug.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <pthread.h>
#include <nuttx/fs/fs.h>
#include <nuttx/sensors/sensor.h>
#include <nuttx/wireless/wireless.h>
#include <nuttx/net/mqtt.h>

/* Configuration */
#define WIFI_SSID       "YourWiFiSSID"
#define WIFI_PASSWORD   "YourWiFiPassword"
#define MQTT_BROKER     "mqtt.example.com"
#define MQTT_PORT       1883
#define MQTT_CLIENT_ID  "ESP32Sparrow"
#define MQTT_USERNAME   "username"
#define MQTT_PASSWORD   "password"

/* Device state */
typedef struct {
  char id[32];
  char name[64];
  char type[32];
  char room_id[32];
  bool is_on;
  char value[32];
} device_t;

#define MAX_DEVICES 16
static device_t g_devices[MAX_DEVICES];
static int g_num_devices = 0;
static pthread_mutex_t g_devices_mutex = PTHREAD_MUTEX_INITIALIZER;

/* MQTT client handle */
static struct mqtt_client_s g_mqtt_client;
static bool g_mqtt_connected = false;

/* Function prototypes */
static int wifi_init(void);
static int mqtt_init(void);
static void mqtt_message_callback(FAR struct mqtt_client_s *client, 
                                 FAR const char *topic,
                                 FAR const char *message,
                                 uint16_t message_len);
static void *sensor_thread(void *arg);
static int init_devices(void);
static int toggle_device(const char *device_id);
static int update_device_value(const char *device_id, const char *value);
static void publish_device_state(const device_t *device);

/**
 * Main application entry point
 */
int main(int argc, FAR char *argv[])
{
  int ret;
  pthread_t thread;

  printf("Smart Home Controller starting...\n");

  /* Initialize devices */
  ret = init_devices();
  if (ret < 0) {
    printf("Failed to initialize devices: %d\n", ret);
    return EXIT_FAILURE;
  }

  /* Initialize WiFi */
  ret = wifi_init();
  if (ret < 0) {
    printf("Failed to initialize WiFi: %d\n", ret);
    return EXIT_FAILURE;
  }

  /* Initialize MQTT */
  ret = mqtt_init();
  if (ret < 0) {
    printf("Failed to initialize MQTT: %d\n", ret);
    return EXIT_FAILURE;
  }

  /* Start sensor thread */
  ret = pthread_create(&thread, NULL, sensor_thread, NULL);
  if (ret != 0) {
    printf("Failed to create sensor thread: %d\n", ret);
    return EXIT_FAILURE;
  }

  /* Main loop */
  while (1) {
    /* Process MQTT messages */
    if (g_mqtt_connected) {
      mqtt_process(&g_mqtt_client);
    } else {
      /* Try to reconnect */
      mqtt_init();
      sleep(5);
    }

    usleep(100000); /* 100ms */
  }

  return EXIT_SUCCESS;
}

/**
 * Initialize WiFi connection
 */
static int wifi_init(void)
{
  int ret;
  struct wifi_connect_req_s req;

  printf("Connecting to WiFi network: %s\n", WIFI_SSID);

  /* Set up connection request */
  memset(&req, 0, sizeof(req));
  req.ssid = WIFI_SSID;
  req.ssid_len = strlen(WIFI_SSID);
  req.bssid = NULL;
  req.password = WIFI_PASSWORD;
  req.password_len = strlen(WIFI_PASSWORD);

  /* Connect to WiFi network */
  ret = wifi_connect(&req);
  if (ret < 0) {
    printf("Failed to connect to WiFi: %d\n", ret);
    return ret;
  }

  printf("Connected to WiFi network\n");
  return OK;
}

/**
 * Initialize MQTT client
 */
static int mqtt_init(void)
{
  int ret;
  struct mqtt_connect_client_info_s client_info;

  /* Set up client info */
  memset(&client_info, 0, sizeof(client_info));
  client_info.client_id = MQTT_CLIENT_ID;
  client_info.client_id_len = strlen(MQTT_CLIENT_ID);
  client_info.client_user = MQTT_USERNAME;
  client_info.client_user_len = strlen(MQTT_USERNAME);
  client_info.client_pass = MQTT_PASSWORD;
  client_info.client_pass_len = strlen(MQTT_PASSWORD);
  client_info.keep_alive = 60;
  client_info.will_topic = NULL;
  client_info.will_msg = NULL;
  client_info.will_qos = 0;
  client_info.will_retain = 0;

  /* Connect to MQTT broker */
  ret = mqtt_connect(&g_mqtt_client, MQTT_BROKER, MQTT_PORT, &client_info, 
                    mqtt_message_callback);
  if (ret != OK) {
    printf("Failed to connect to MQTT broker: %d\n", ret);
    return ret;
  }

  /* Subscribe to device control topics */
  ret = mqtt_subscribe(&g_mqtt_client, "home/devices/+/control", 0);
  if (ret != OK) {
    printf("Failed to subscribe to control topics: %d\n", ret);
    return ret;
  }

  g_mqtt_connected = true;
  printf("Connected to MQTT broker\n");

  /* Publish initial device states */
  pthread_mutex_lock(&g_devices_mutex);
  for (int i = 0; i < g_num_devices; i++) {
    publish_device_state(&g_devices[i]);
  }
  pthread_mutex_unlock(&g_devices_mutex);

  return OK;
}

/**
 * MQTT message callback
 */
static void mqtt_message_callback(FAR struct mqtt_client_s *client, 
                                 FAR const char *topic,
                                 FAR const char *message,
                                 uint16_t message_len)
{
  char device_id[32];
  char msg_copy[128];

  /* Extract device ID from topic */
  if (sscanf(topic, "home/devices/%31[^/]/control", device_id) != 1) {
    printf("Invalid topic format: %s\n", topic);
    return;
  }

  /* Copy message to null-terminated string */
  if (message_len >= sizeof(msg_copy)) {
    message_len = sizeof(msg_copy) - 1;
  }
  memcpy(msg_copy, message, message_len);
  msg_copy[message_len] = '\0';

  printf("Received message: %s for device: %s\n", msg_copy, device_id);

  /* Process command */
  if (strcmp(msg_copy, "toggle") == 0) {
    toggle_device(device_id);
  } else if (strncmp(msg_copy, "value:", 6) == 0) {
    update_device_value(device_id, msg_copy + 6);
  }
}

/**
 * Sensor reading thread
 */
static void *sensor_thread(void *arg)
{
  int temp_fd;
  struct sensor_temp temp_data;
  char value[32];
  
  /* Open temperature sensor */
  temp_fd = open("/dev/temp0", O_RDONLY);
  if (temp_fd < 0) {
    printf("Failed to open temperature sensor: %d\n", errno);
    return NULL;
  }

  while (1) {
    /* Read temperature sensor */
    if (read(temp_fd, &temp_data, sizeof(temp_data)) == sizeof(temp_data)) {
      /* Update temperature device */
      snprintf(value, sizeof(value), "%.1f", temp_data.temperature);
      update_device_value("temp_sensor", value);
    }

    /* Sleep for 30 seconds */
    sleep(30);
  }

  close(temp_fd);
  return NULL;
}

/**
 * Initialize device list
 */
static int init_devices(void)
{
  pthread_mutex_lock(&g_devices_mutex);

  /* Add some example devices */
  
  /* Living Room Light */
  strcpy(g_devices[g_num_devices].id, "living_light");
  strcpy(g_devices[g_num_devices].name, "Living Room Light");
  strcpy(g_devices[g_num_devices].type, "LIGHT");
  strcpy(g_devices[g_num_devices].room_id, "living");
  g_devices[g_num_devices].is_on = false;
  g_devices[g_num_devices].value[0] = '\0';
  g_num_devices++;

  /* Kitchen Light */
  strcpy(g_devices[g_num_devices].id, "kitchen_light");
  strcpy(g_devices[g_num_devices].name, "Kitchen Light");
  strcpy(g_devices[g_num_devices].type, "LIGHT");
  strcpy(g_devices[g_num_devices].room_id, "kitchen");
  g_devices[g_num_devices].is_on = false;
  g_devices[g_num_devices].value[0] = '\0';
  g_num_devices++;

  /* Thermostat */
  strcpy(g_devices[g_num_devices].id, "thermostat");
  strcpy(g_devices[g_num_devices].name, "Thermostat");
  strcpy(g_devices[g_num_devices].type, "THERMOSTAT");
  strcpy(g_devices[g_num_devices].room_id, "living");
  g_devices[g_num_devices].is_on = true;
  strcpy(g_devices[g_num_devices].value, "72.0");
  g_num_devices++;

  /* Temperature Sensor */
  strcpy(g_devices[g_num_devices].id, "temp_sensor");
  strcpy(g_devices[g_num_devices].name, "Temperature Sensor");
  strcpy(g_devices[g_num_devices].type, "SENSOR");
  strcpy(g_devices[g_num_devices].room_id, "bedroom");
  g_devices[g_num_devices].is_on = true;
  strcpy(g_devices[g_num_devices].value, "70.0");
  g_num_devices++;

  /* Front Door Lock */
  strcpy(g_devices[g_num_devices].id, "front_lock");
  strcpy(g_devices[g_num_devices].name, "Front Door");
  strcpy(g_devices[g_num_devices].type, "LOCK");
  strc  "Front Door");
  strcpy(g_devices[g_num_devices].type, "LOCK");
  strcpy(g_devices[g_num_devices].room_id, "entrance");
  g_devices[g_num_devices].is_on = false;
  g_devices[g_num_devices].value[0] = '\0';
  g_num_devices++;

  pthread_mutex_unlock(&g_devices_mutex);
  
  /* Initialize GPIO pins for device control */
  /* Note: This would be platform-specific for ESP32 Sparrow */
  
  return OK;
}

/**
 * Toggle device state
 */
static int toggle_device(const char *device_id)
{
  int i;
  bool found = false;
  device_t *device = NULL;
  
  pthread_mutex_lock(&g_devices_mutex);
  
  /* Find device by ID */
  for (i = 0; i < g_num_devices; i++) {
    if (strcmp(g_devices[i].id, device_id) == 0) {
      device = &g_devices[i];
      found = true;
      break;
    }
  }
  
  if (!found) {
    pthread_mutex_unlock(&g_devices_mutex);
    printf("Device not found: %s\n", device_id);
    return -ENODEV;
  }
  
  /* Toggle device state */
  device->is_on = !device->is_on;
  
  /* Perform hardware control based on device type */
  if (strcmp(device->type, "LIGHT") == 0) {
    /* Control GPIO pin for light */
    printf("Setting %s to %s\n", device->name, device->is_on ? "ON" : "OFF");
    /* GPIO control code would go here */
  } else if (strcmp(device->type, "LOCK") == 0) {
    /* Control GPIO pin for lock */
    printf("Setting %s to %s\n", device->name, device->is_on ? "LOCKED" : "UNLOCKED");
    /* GPIO control code would go here */
  }
  
  /* Publish updated state */
  publish_device_state(device);
  
  pthread_mutex_unlock(&g_devices_mutex);
  return OK;
}

/**
 * Update device value
 */
static int update_device_value(const char *device_id, const char *value)
{
  int i;
  bool found = false;
  device_t *device = NULL;
  
  pthread_mutex_lock(&g_devices_mutex);
  
  /* Find device by ID */
  for (i = 0; i < g_num_devices; i++) {
    if (strcmp(g_devices[i].id, device_id) == 0) {
      device = &g_devices[i];
      found = true;
      break;
    }
  }
  
  if (!found) {
    pthread_mutex_unlock(&g_devices_mutex);
    printf("Device not found: %s\n", device_id);
    return -ENODEV;
  }
  
  /* Update device value */
  strncpy(device->value, value, sizeof(device->value) - 1);
  device->value[sizeof(device->value) - 1] = '\0';
  
  /* Perform hardware control based on device type */
  if (strcmp(device->type, "THERMOSTAT") == 0) {
    /* Control thermostat based on value */
    printf("Setting %s to %s degrees\n", device->name, device->value);
    /* Control code would go here */
  }
  
  /* Publish updated state */
  publish_device_state(device);
  
  pthread_mutex_unlock(&g_devices_mutex);
  return OK;
}

/**
 * Publish device state to MQTT
 */
static void publish_device_state(const device_t *device)
{
  char topic[128];
  char payload[256];
  
  if (!g_mqtt_connected) {
    return;
  }
  
  /* Create topic */
  snprintf(topic, sizeof(topic), "home/devices/%s/state", device->id);
  
  /* Create JSON payload */
  snprintf(payload, sizeof(payload), 
          "{\"id\":\"%s\",\"name\":\"%s\",\"type\":\"%s\",\"room_id\":\"%s\",\"is_on\":%s,\"value\":\"%s\"}",
          device->id, device->name, device->type, device->room_id,
          device->is_on ? "true" : "false", device->value);
  
  /* Publish message */
  mqtt_publish(&g_mqtt_client, topic, payload, strlen(payload), 0, 0);
}
