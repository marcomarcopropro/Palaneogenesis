# Branding

Assets fuente del arte de Palaneogénesis. Cada archivo está pensado para un contexto
distinto (lo dice su nombre):

| Archivo | Contexto | Dónde vive / qué hacer con él |
| --- | --- | --- |
| `pack_128.png` | Ícono del resource pack del mod | Copiado a `src/main/resources/pack.png` (se ve en la pantalla de "Resource Packs" de Minecraft, al lado de `pack.mcmeta`). |
| `logo_256.png` | Logo del mod | Copiado a `src/main/resources/logo.png` y referenciado como `logoFile="logo.png"` en `META-INF/mods.toml` (se ve en la pantalla de "Mods" dentro del juego). |
| `icon_512_curseforge_modrinth.png` | Ícono de las páginas del proyecto | No lo carga el juego - es el archivo para subir manualmente como ícono del proyecto en CurseForge y en Modrinth cuando se publique/actualice la página. |
| `banner_original_1691x930.png` | Banner del repositorio | Usado como imagen de cabecera en el `README.md` del repo (contexto GitHub). |

Los archivos ya copiados a `src/main/resources` (`pack.png`, `logo.png`) son la copia que
efectivamente usa el build - si el arte cambia, actualizar ambos lados (acá y en
`src/main/resources`).
