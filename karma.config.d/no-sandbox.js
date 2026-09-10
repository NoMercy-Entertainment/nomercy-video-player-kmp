// Chrome will not start as root without --no-sandbox, and the self-hosted
// Linux runner executes as root — the GitHub-hosted image ran as a normal user,
// so this never came up there. Without it the wasm tests fail with
// "Running as root without --no-sandbox is not supported".
//
// --disable-dev-shm-usage for the same environment: a container's default
// /dev/shm is 64 MB and Chrome crashes part-way through a run rather than
// failing to launch, which is the harder version of this to read.
//
// Core hit both halves of this on 2026-09-10; this module has the same
// wasmJs { browser() } target, so it gets the same config before it does.
config.set({
    customLaunchers: {
        ChromeHeadlessNoSandbox: {
            base: 'ChromeHeadless',
            flags: ['--no-sandbox', '--disable-dev-shm-usage'],
        },
    },
    browsers: ['ChromeHeadlessNoSandbox'],
});
