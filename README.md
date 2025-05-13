# FastLogin

![A shield-shaped emblem with a bold lightning bolt on the left, resembling Minecraft blocks. To the right, "FastLogin" is written in teal, with the tagline: "Automatically detect and login premium Minecraft players"](https://github.com/user-attachments/assets/0788ef69-029b-465e-83a2-b8e7bccc6295 "FastLogin project logo.avif")

Checks if a Minecraft player has a paid account (premium). If so, they can skip offline authentication (auth plugins).
So they don't need to enter passwords. This is also called auto login (auto-login).

## Features

* Detect paid accounts from others
* Automatically login paid accounts (premium)
* Support various of auth plugins
* Premium UUID support
* Forward skins
* Detect username changed and will update the existing database record
* BungeeCord/Velocity support
* Auto register new premium players
* No client modifications needed
* Good performance by using async operations
* Locale messages
* Support for Bedrock players proxies through FloodGate

## Issues

Please use issues for bug reports, suggestions, questions and more. Please check for existing issues. Existing issues
can be voted up by adding up vote to the original post. Closing issues means that they are marked as resolved. Comments
are still allowed and it could be re-opened.

## Development builds

Development builds contain the latest changes from the Source-Code. They are bleeding edge and could introduce new bugs,
but also include features, enhancements and bug fixes that are not yet in a released version. If you click on the left
side on `Changes`, you can see iterative change sets leading to a specific build.

You can download them from here: [CodeMC(Jenkins)](https://ci.codemc.org/job/Games647/job/FastLogin/)

***

## Commands

    /premium [player] Label the invoker or the argument as paid account
    /cracked [player] Label the invoker or the argument as cracked account

## Permissions

    fastlogin.bukkit.command.premium
    fastlogin.bukkit.command.cracked

    fastlogin.command.premium.other
    fastlogin.command.cracked.other

## Placeholder

This plugin supports `PlaceholderAPI` on `Spigot`. It exports the following variable
`%fastlogin_status%`. In BungeeCord environments, the status of a player will be delivered with a delay after the player
already successful joined the server. This takes about a couple of milliseconds. In this case the value
will be `Unknown`.

Possible values: `Premium`, `Cracked`, `Unknown`

## Requirements

* Java: 21+ recommended for improved multi-threading code by FastLogin
  * Spigot: 8+
  * BungeeCord and Velocity: 17+
* Server software in offlinemode:
  * Spigot (or a fork e.g. Paper) 1.8.8+
    * Protocol plugin:
      * [ProtocolLib 5.3+ with development build above 720](https://www.spigotmc.org/resources/protocollib.1997/) or
      * [ProtocolSupport](https://www.spigotmc.org/resources/protocolsupport.7201/)
  * Latest BungeeCord (or a fork e.g. Waterfall) or Velocity proxy
* An auth plugin.

### Supported auth plugins

#### Spigot/Paper

* [AdvancedLogin (Paid)](https://www.spigotmc.org/resources/advancedlogin.10510/)
* [AuthMe (5.X)](https://dev.bukkit.org/bukkit-plugins/authme-reloaded/)
* [CrazyLogin](https://dev.bukkit.org/bukkit-plugins/crazylogin/)
* [LoginSecurity](https://dev.bukkit.org/bukkit-plugins/loginsecurity/)
* [LogIt](https://github.com/games647/LogIt)
* [UltraAuth](https://dev.bukkit.org/bukkit-plugins/ultraauth-aa/)
* [UserLogin](https://www.spigotmc.org/resources/userlogin.80669/)
* [xAuth](https://dev.bukkit.org/bukkit-plugins/xauth/)

#### BungeeCord/Waterfall

* [BungeeAuth](https://www.spigotmc.org/resources/bungeeauth.493/)

## Network requests

This plugin performs network requests to:

* https://api.mojang.com - retrieving uuid data to decide if we should activate premium login
* https://sessionserver.mojang.com - verify if the player is the owner of that account

***

## How to install

### Spigot/Paper

1. Download and install ProtocolLib/ProtocolSupport
2. Download and install `FastLoginBukkit`
3. Set your server in offline mode by setting the value `onlinemode` in your server.properties to `false`

### BungeeCord/Waterfall or Velocity

Install the plugin on both platforms, that is proxy (BungeeCord or Velocity) and backend server (Spigot).

1. Activate proxy support in the server configuration
   * This is often found in `spigot.yml` or `paper.yml`
2. Restart the backend server
3. Now there is `allowed-proxies.txt` file in the FastLogin folder of the restarted server
    * BungeeCord: Put your `stats`-id from the BungeeCord config into this file
    * Velocity: On plugin startup the plugin generates a `proxyId.txt` inside the plugins folder of the proxy
4. Activate ip forwarding in your proxy config
5. Check your database settings in the config of FastLogin on your proxy
    * The proxies only ship with a limited set of drivers where Spigot supports more. Therefore, these are supported:
    * BungeeCord: `mysql` for MySQL/MariaDB
    * Velocity: `mariadb` for MySQL/MariaDB
    * Note the embedded file storage SQLite is not available
    * MySQL/MariaDB requires an external database server running. Check your server provider if there is one available
   or install one.
6. Set proxy and Spigot in offline mode by setting the value `onlinemode` in your `config.yml` to false
7. You should *always* configure the firewall for your Spigot server so that it's only accessible through your proxy
   * This is also the case without this plugin
   * https://www.spigotmc.org/wiki/bungeecord-installation/#post-installation
