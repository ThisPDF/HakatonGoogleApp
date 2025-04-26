import { DashboardHeader } from "@/components/dashboard-header"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Cpu, RefreshCw, Wifi } from "lucide-react"
import { Button } from "@/components/ui/button"

export default function ControllersPage() {
  return (
    <main className="flex min-h-screen flex-col p-4 md:p-6">
      <DashboardHeader />

      <div className="mt-6">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>ESP32 Sparrow Rev 2</CardTitle>
                <CardDescription>Smart Home Controller</CardDescription>
              </div>
              <Cpu className="h-5 w-5 text-muted-foreground" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Wifi className="h-4 w-4 text-green-500" />
                  <span>Connected</span>
                </div>
                <Button size="sm" variant="outline">
                  <RefreshCw className="mr-2 h-4 w-4" />
                  Refresh
                </Button>
              </div>

              <Tabs defaultValue="status">
                <TabsList className="grid w-full grid-cols-3">
                  <TabsTrigger value="status">Status</TabsTrigger>
                  <TabsTrigger value="config">Configuration</TabsTrigger>
                  <TabsTrigger value="logs">Logs</TabsTrigger>
                </TabsList>
                <TabsContent value="status" className="space-y-4 pt-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <div className="text-sm font-medium text-muted-foreground">IP Address</div>
                      <div>192.168.1.100</div>
                    </div>
                    <div>
                      <div className="text-sm font-medium text-muted-foreground">MAC Address</div>
                      <div>AA:BB:CC:DD:EE:FF</div>
                    </div>
                    <div>
                      <div className="text-sm font-medium text-muted-foreground">Uptime</div>
                      <div>3 days, 7 hours</div>
                    </div>
                    <div>
                      <div className="text-sm font-medium text-muted-foreground">Firmware</div>
                      <div>v1.2.3</div>
                    </div>
                  </div>
                </TabsContent>
                <TabsContent value="config" className="pt-4">
                  <p className="text-sm text-muted-foreground">
                    Configuration settings for your ESP32 Sparrow Rev 2 device would appear here.
                  </p>
                </TabsContent>
                <TabsContent value="logs" className="pt-4">
                  <div className="rounded-md bg-muted p-4">
                    <pre className="text-xs">
                      [INFO] 2023-04-26 11:42:10: Device started
                      <br />
                      [INFO] 2023-04-26 11:42:12: Connected to WiFi
                      <br />
                      [INFO] 2023-04-26 11:42:15: MQTT broker connected
                      <br />
                      [INFO] 2023-04-26 11:45:30: Living room lights turned on
                      <br />
                      [INFO] 2023-04-26 12:30:45: Temperature sensor reading: 22.5°C
                    </pre>
                  </div>
                </TabsContent>
              </Tabs>
            </div>
          </CardContent>
        </Card>
      </div>
    </main>
  )
}
