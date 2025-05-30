# Development instructions cheat sheet

## Other folders other than the Android project
- `beacons/`: contains the configuration for the InBeacon beacons.

## Release process
1. Update the version code and name in `build.gradle` for `Module :app`.
2. Tag the commit with the new version name, e.g., `v1.0.0`. You can also specify a prerelease candidate, e.g., `v1.0.0-rc1` or `v1.0.0-beta1`.
3. Push the changes to the `main` branch.

## Breaking changes
- Made to the session files, may require updating the session readers and the server image before making a production release. Verify if the server currently does a `surCO - Reader` based validation and update the server if needed.
