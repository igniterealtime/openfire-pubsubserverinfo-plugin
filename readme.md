# Openfire PubSub Server Info Plugin

The PubSub Server Info plugin provides a way for Openfire to report statistics about itself in a well-known pub-sub
node: 'serverinfo'. The data format is defined in [XEP-0485: PubSub Server Info](https://xmpp.org/extensions/xep-0485.html).

This plugin is what could be used by applications such as the [XMPP Network Graph](https://xmppnetwork.goodbytes.im)

## Installation
Copy pubsubserverinfo.jar into the plugins directory of your Openfire installation. The plugin will then be
automatically deployed. To upgrade to a new version, copy the new pubsubserverinfo.jar file over the existing
file.

## Reporting Issues

Issues may be reported to the [forums](https://discourse.igniterealtime.org) or via this repo's [Github Issues](https://github.com/igniterealtime/openfire-pubsubserverinfo-plugin).
