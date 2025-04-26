"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"

export function RoomSelector() {
  const [selectedRoom, setSelectedRoom] = useState("living-room")

  return (
    <div className="space-y-2">
      <h2 className="text-lg font-semibold">Rooms</h2>
      <div className="flex flex-wrap gap-2">
        <Button
          variant={selectedRoom === "living-room" ? "default" : "outline"}
          onClick={() => setSelectedRoom("living-room")}
          className="rounded-full"
        >
          Living Room
        </Button>
        <Button
          variant={selectedRoom === "kitchen" ? "default" : "outline"}
          onClick={() => setSelectedRoom("kitchen")}
          className="rounded-full"
        >
          Kitchen
        </Button>
        <Button
          variant={selectedRoom === "bedroom" ? "default" : "outline"}
          onClick={() => setSelectedRoom("bedroom")}
          className="rounded-full"
        >
          Bedroom
        </Button>
        <Button
          variant={selectedRoom === "bathroom" ? "default" : "outline"}
          onClick={() => setSelectedRoom("bathroom")}
          className="rounded-full"
        >
          Bathroom
        </Button>
      </div>
    </div>
  )
}
