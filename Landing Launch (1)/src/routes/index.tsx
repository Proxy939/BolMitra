import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Bolmitra — Android App Source" },
      {
        name: "description",
        content:
          "This repository contains the native Android (Jetpack Compose) source for Bolmitra under the android/ folder.",
      },
    ],
  }),
  component: Placeholder,
});

function Placeholder() {
  return (
    <main className="flex min-h-screen items-center justify-center p-8">
      <div className="max-w-md text-center space-y-3">
        <h1 className="text-2xl font-semibold">Bolmitra — Android source</h1>
        <p className="text-muted-foreground">
          The web landing page has been removed. The native Android app code
          (Kotlin + Jetpack Compose) lives in the{" "}
          <code className="font-mono">android/</code> folder — open it in
          Android Studio.
        </p>
      </div>
    </main>
  );
}
