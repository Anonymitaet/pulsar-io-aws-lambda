exports.handler = async function(event, context) {
    for (const [key, value] of Object.entries(event)) {
        if (typeof value === 'string' || value instanceof String) {
            event[key] = value + "!";
        }
    }
    return event
}
