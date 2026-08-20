let tickCounter = 0
ServerEvents.tick(event => {
    tickCounter++
    if (tickCounter % 20 === 0) {
        const server = event.server
        const players = server.getPlayers()
        if (players.isEmpty()) return
        server.runCommandSilent('/effect give @a minecraft:resistance infinite 255 true')
        server.runCommandSilent('/effect give @a minecraft:saturation infinite 255 true')
    }
})