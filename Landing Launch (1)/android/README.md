# Bolmitra Compose UI

Compose source for the landing (welcome) screen and the post-login dashboard,
written against the stack you described: Kotlin 2.4.x, AGP 9.4 built-in Kotlin,
Compose BOM 2026.08.00, Material 3, minSdk 28, Java 17.

## Where to put these

Copy the `org/bolmitra/ui` tree into `app/src/main/java/`:

```
app/src/main/java/org/bolmitra/ui/
  theme/Color.kt
  theme/Type.kt
  theme/Theme.kt
  common/GlassCard.kt
  common/Charts.kt
  landing/LandingScreen.kt
  dashboard/DashboardModels.kt
  dashboard/DashboardScreen.kt
  dashboard/DashboardCards.kt
  BolmitraApp.kt
```

## Wiring

`MainActivity` only needs:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BolmitraApp() }
    }
}
```

`BolmitraApp` holds a single `rememberSaveable` flag that swaps landing ->
dashboard, so there is no navigation dependency to add. Replace that flag with
your real auth/session state when it exists.

## Dependencies

Everything used here is already in your catalog: `activity-compose`,
`compose-bom`, `material3`, `ui`, `ui-graphics`, `ui-tooling-preview`,
`lifecycle-runtime-ktx`, `core-ktx`. Charts are drawn with `Canvas` from
`compose.foundation`, so no charting library is introduced.

Add nothing else. Icons use `material-icons-extended` if you already have it;
if not, the two icon references in `DashboardScreen.kt` fall back to
`Icons.Filled.*` from the core icon set — see the comment there.

## Design notes

- Monochrome only: pure ink `#0E0E0E`, paper `#F2F2F2`, plus translucent white
  surfaces for the "frosted panel" look. All values live in `theme/Color.kt`.
- The glass effect is layered translucent white + hairline border + soft
  elevation shadow (`GlassCard`). Real blur (`Modifier.blur`) is applied only to
  the backdrop wash, which is cheap and works from API 31; on API 28-30 it
  degrades to the flat gradient automatically.
- Dark cards (`InkCard`) invert to paper-on-ink for the emphasis panels
  (Overall Information, task cards, project cards).
- Layout is responsive: the sidebar is a permanent rail at >= 840dp width and
  collapses into a bottom bar below that, and the three top cards reflow from a
  row into a stacked column. Sized for tablet first, usable on phone.
- Everything is stateless composables fed by `DashboardUiState`, so you can hand
  it Room-backed data later without touching the UI.
