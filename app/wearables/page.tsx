import { DashboardHeader } from "@/components/dashboard-header"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { RefreshCw, Watch } from "lucide-react"

export default function WearablesPage() {
  return (
    <main className="flex min-h-screen flex-col p-4 md:p-6">
      <DashboardHeader />

      <div className="mt-6">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>WearOS Device</CardTitle>
                <CardDescription>Smart Watch Controller</CardDescription>
              </div>
              <Watch className="h-5 w-5 text-muted-foreground" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="h-3 w-3 rounded-full bg-green-500"></div>
                  <span>Connected</span>
                </div>
                <Button size="sm" variant="outline">
                  <RefreshCw className="mr-2 h-4 w-4" />
                  Refresh
                </Button>
              </div>

              <Tabs defaultValue="controls">
                <TabsList className="grid w-full grid-cols-3">
                  <TabsTrigger value="controls">Controls</TabsTrigger>
                  <TabsTrigger value="settings">Settings</TabsTrigger>
                  <TabsTrigger value="sync">Sync</TabsTrigger>
                </TabsList>
                <TabsContent value="controls" className="space-y-4 pt-4">
                  <p className="text-sm text-muted-foreground mb-4">Quick controls available on your WearOS device:</p>
                  <div className="grid grid-cols-2 gap-3">
                    <Button variant="outline" className="h-20 flex flex-col items-center justify-center">
                      <div className="text-2xl mb-1">💡</div>
                      <div className="text-xs">Lights</div>
                    </Button>
                    <Button variant="outline" className="h-20 flex flex-col items-center justify-center">
                      <div className="text-2xl mb-1">🔒</div>
                      <div className="text-xs">Lock</div>
                    </Button>
                    <Button variant="outline" className="h-20 flex flex-col items-center justify-center">
                      <div className="text-2xl mb-1">🌡️</div>
                      <div className="text-xs">Temp</div>
                    </Button>
                    <Button variant="outline" className="h-20 flex flex-col items-center justify-center">
                      <div className="text-2xl mb-1">📺</div>
                      <div className="text-xs">TV</div>
                    </Button>
                  </div>
                </TabsContent>
                <TabsContent value="settings" className="pt-4">
                  <p className="text-sm text-muted-foreground">WearOS app settings would appear here.</p>
                </TabsContent>
                <TabsContent value="sync" className="pt-4">
                  <div className="space-y-4">
                    <p className="text-sm text-muted-foreground">Last synchronized: 10 minutes ago</p>
                    <Button>Sync Now</Button>
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
