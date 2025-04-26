import { DashboardHeader } from "@/components/dashboard-header"
import { DeviceControl } from "@/components/device-control"
import { RoomSelector } from "@/components/room-selector"
import { StatusCard } from "@/components/status-card"

export default function HomePage() {
  return (
    <main className="flex min-h-screen flex-col p-4 md:p-6">
      <DashboardHeader />
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3 mt-4">
        <StatusCard title="Living Room" value="4 devices" description="2 devices active" icon="sofa" />
        <StatusCard title="Kitchen" value="3 devices" description="1 device active" icon="utensils" />
        <StatusCard title="Bedroom" value="2 devices" description="All inactive" icon="bed" />
      </div>
      <div className="mt-6">
        <RoomSelector />
      </div>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3 mt-6">
        <DeviceControl name="Living Room Lights" type="light" status="on" icon="lamp" />
        <DeviceControl name="TV" type="media" status="off" icon="tv" />
        <DeviceControl name="Air Conditioner" type="climate" status="on" icon="thermometer" value="22°C" />
        <DeviceControl name="Smart Speaker" type="audio" status="off" icon="speaker" />
      </div>
    </main>
  )
}
