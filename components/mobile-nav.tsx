"use client"

import { Home, Smartphone, Watch, Cpu, Settings, LogOut } from "lucide-react"
import Link from "next/link"

interface MobileNavProps {
  onNavigate: () => void
}

export function MobileNav({ onNavigate }: MobileNavProps) {
  return (
    <div className="flex h-full flex-col bg-background">
      <div className="px-6 py-4 border-b">
        <h2 className="text-lg font-semibold">Smart Home Control</h2>
        <p className="text-sm text-muted-foreground">Manage your devices</p>
      </div>
      <nav className="flex-1 overflow-auto py-2">
        <div className="px-3">
          <div className="space-y-1">
            <Link
              href="/"
              onClick={onNavigate}
              className="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium bg-accent"
            >
              <Home className="h-4 w-4" />
              Dashboard
            </Link>
            <Link
              href="/devices"
              onClick={onNavigate}
              className="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium hover:bg-accent"
            >
              <Smartphone className="h-4 w-4" />
              Mobile Devices
            </Link>
            <Link
              href="/wearables"
              onClick={onNavigate}
              className="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium hover:bg-accent"
            >
              <Watch className="h-4 w-4" />
              WearOS Devices
            </Link>
            <Link
              href="/controllers"
              onClick={onNavigate}
              className="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium hover:bg-accent"
            >
              <Cpu className="h-4 w-4" />
              ESP32 Controller
            </Link>
          </div>
        </div>
      </nav>
      <div className="border-t px-3 py-4">
        <div className="space-y-1">
          <Link
            href="/settings"
            onClick={onNavigate}
            className="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium hover:bg-accent"
          >
            <Settings className="h-4 w-4" />
            Settings
          </Link>
          <button className="w-full flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium hover:bg-accent">
            <LogOut className="h-4 w-4" />
            Log out
          </button>
        </div>
      </div>
    </div>
  )
}
