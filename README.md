# WearOS & ESP32 Integration App

A full-stack application integrating WearOS and ESP32 for real-time data synchronization and monitoring.

## 🌐 Live Demo

**Deployed on Vercel** – URL can be added here after deployment.

## 📦 Project Structure

* `app/` – Next.js frontend layout and pages
* `app/api/esp32/route.ts` – API route for ESP32 communication
* `components.json` – Component mapping
* `gradle/` – Android-related build files for WearOS
* `tailwind.config.ts` – TailwindCSS config
* `package.json` – NPM dependencies
* `build.gradle` & `settings.gradle.kts` – Android build system configs

## ⚙️ Technologies Used

* **Next.js 14**
* **TypeScript**
* **TailwindCSS**
* **WearOS**
* **ESP32 (HTTP-based communication)**
* **Vercel (Deployment)**

## 🚀 How to Run Locally

### Frontend (Next.js)

```bash
# Install dependencies
npm install

# Start development server
npm run dev
```

### Backend API for ESP32

Accessible at `/api/esp32` route in your Next.js app.
Configure your ESP32 to POST data to:

```
http://<your-local-ip>:3000/api/esp32
```

### WearOS App (Android)

Open the project in Android Studio and run it on a WearOS emulator or device.

## 🛠 Development

Work locally using your preferred IDE or code editor.

## 📝 License

MIT License.
