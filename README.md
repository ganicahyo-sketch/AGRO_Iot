# AGRO-IoT GitHub Actions run 39820804671 — resource linking fix

The build failure from `:app:processDebugResources` is caused by this invalid Android view ID in:

`app/src/main/res/layout/activity_main.xml`

Before:

```xml
android:id="root"
```

After:

```xml
android:id="@+id/root"
```

Apply the included `root-id-fix.patch` from the repository root:

```bash
git apply root-id-fix.patch
git add app/src/main/res/layout/activity_main.xml
git commit -m "Fix invalid root layout ID"
git push origin main
```

The new push should start the workflow. Check the new Actions run and confirm that `:app:processDebugResources` succeeds, then inspect any new errors if the build proceeds further.
