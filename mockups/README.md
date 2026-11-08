# PVPS UI explorations

Static, interactive HTML prototypes used to choose a direction before touching
Kotlin. Open `index.html` from the repository root:

```bash
python3 -m http.server 8090 --directory mockups
```

## Layout experiments (A–E)

These change the information architecture, not just the skin. Each one has
working navigation and a second screen to judge the system.

| File | Concept | Navigation | Home is |
| --- | --- | --- | --- |
| `A-console.html` | Console | command palette (`⌘`), sheets | a timeline of events |
| `B-board.html` | Board | tap tile → full detail, bottom rail | a mosaic of live widgets |
| `C-pages.html` | Pages | horizontal swipe, pager dots | four full-bleed pages |
| `D-rail.html` | Rail | left tool rail + inspector | a master/detail list |
| `E-triage.html` | Triage | deck actions + four tabs | an inbox of exceptions |

## Visual studies (01–05)

Kept as references for palette and type only; the layout is the same stacked
dashboard in all five.

| File | Language |
| --- | --- |
| `01-editorial.html` | paper, serif, hairline rules |
| `02-brutalist.html` | hard borders, offset shadows, lime |
| `03-glass.html` | light mesh, blur, big radii |
| `04-swiss.html` | dark, 1px lines, indigo accent, dense |
| `05-phosphor.html` | CRT green, scanlines, ASCII |

## Porting notes

Whatever wins has to keep the existing contracts in
`app/src/main/java/dev/pvps/ui`: `AppViewModel` exposes hosts, connection
states, OpenCode state and system snapshots; screens only read those and call
back. A new shell (rail, pager, deck) can replace `AppRoot`'s bottom bar
without touching SSH, SFTP or the OpenCode repository.
