import { Bed, Cpu, Home, type LucideIcon, Sofa, Thermometer, Utensils } from "lucide-react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

interface StatusCardProps {
  title: string
  value: string
  description: string
  icon: string
}

export function StatusCard({ title, value, description, icon }: StatusCardProps) {
  const getIcon = (): LucideIcon => {
    switch (icon) {
      case "home":
        return Home
      case "sofa":
        return Sofa
      case "bed":
        return Bed
      case "utensils":
        return Utensils
      case "thermometer":
        return Thermometer
      case "cpu":
        return Cpu
      default:
        return Home
    }
  }

  const Icon = getIcon()

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium">{title}</CardTitle>
        <Icon className="h-4 w-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        <CardDescription>{description}</CardDescription>
      </CardContent>
    </Card>
  )
}
