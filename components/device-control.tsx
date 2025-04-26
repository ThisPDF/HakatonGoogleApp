"use client"

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Switch } from "@/components/ui/switch"
import { Lamp, Tv, Speaker, Thermometer, type LucideIcon } from "lucide-react"

interface DeviceControlProps {
  name: string
  type: string
  status: "on" | "off"
  icon: string
  value?: string
}

export function DeviceControl({ name, type, status: initialStatus, icon, value }: DeviceControlProps) {
  const [status, setStatus] = useState<"on" | "off">(initialStatus)

  const getIcon = (): LucideIcon => {
    switch (icon) {
      case "lamp":
        return Lamp
      case "tv":
        return Tv
      case "speaker":
        return Speaker
      case "thermometer":
        return Thermometer
      default:
        return Lamp
    }
  }

  const Icon = getIcon()

  const handleToggle = () => {
    const newStatus = status === "on" ? "off" : "on"
    setStatus(newStatus)

    // Here you would send a request to your API to control the actual device
    console.log(`Toggling ${name} to ${newStatus}`)
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium">{name}</CardTitle>
        <Icon className={`h-4 w-4 ${status === "on" ? "text-primary" : "text-muted-foreground"}`} />
      </CardHeader>
      <CardContent className="pb-3">
        <div className="flex items-center justify-between">
          <CardDescription>
            {status === "on" ? "On" : "Off"}
            {value && status === "on" ? ` • ${value}` : ""}
          </CardDescription>
          <Switch checked={status === "on"} onCheckedChange={handleToggle} />
        </div>
      </CardContent>
    </Card>
  )
}
