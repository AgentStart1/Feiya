export function followPortChanges(source) {
    source.addEventListener('port', function (event) {
        const destination = new URL(window.location.href);
        destination.port = event.data;
        window.location.replace(destination.href);
    });
}
