# Modrinth submission details

| Field | Value |
| --- | --- |
| Project | Mining Dropper |
| Suggested slug | mining-dropper |
| Type | Mod |
| Category | Utility |
| Release | 1.2.3, Release |
| Minecraft version | 26.3 |
| Loader | Fabric |
| Environment | Client-side only |
| Required dependency | Fabric API |
| Optional dependency | Mod Menu |
| Requested visibility | Unlisted, subject to Modrinth review |
| Content disclosures | AI-generated code and AI-generated text |

Use `summary.txt` as the short description, `description.md` as the project body, and `../release/notes.md` for the changelog. Upload the JAR in `../release/`. The release source specifies no open-source license. The publishing workflow uses Modrinth’s All Rights Reserved option.

The owner specified and tested the features. Codex generated the implementation and the page copy. Modrinth describes unlisted distribution as available in most cases for this kind of project, subject to review. [Modrinth’s AI publishing policy](https://support.modrinth.com/en/articles/16551575-disclosure-and-usage-of-ai)

## Submit from GitHub

The **Submit to Modrinth** workflow checks the JAR and release metadata. With a Modrinth token saved in this repository’s Actions secrets as `MODRINTH_TOKEN`, it creates the project and version, sets both AI disclosures, verifies the uploaded file, and requests unlisted review. A repeated run checks the existing submission. Modrinth decides whether to approve it.

Use a project-publishing token with user read, project read/create/write, and version read/create permissions. Enter the token in the GitHub secret field. After adding it, rerun the workflow from the Actions tab.

[Modrinth token settings](https://modrinth.com/settings/pats) · [GitHub Actions secrets](https://github.com/Yanglerfish/mining-dropper/settings/secrets/actions) · [Run the workflow](https://github.com/Yanglerfish/mining-dropper/actions/workflows/modrinth.yml)
