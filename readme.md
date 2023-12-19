# Openfire PubSub Server Info Plugin

The PubSub Server Info plugin provides a way for Openfire to report statistics about itself in a well-known pub-sub
node: 'serverinfo'. The data format is defined in [XEP-xxxx: PubSub Server Info](https://xmpp.org/extensions/inbox/xep-pubsub-server-info.html).

Note: at the time of writing, the protocol as implemented by this plugin has not yet been accepted for consideration or approved 
in any official manner by the XMPP Standards Foundation, and this document is not yet an XMPP Extension Protocol (XEP). This plugin should
be considered experimental.

## Installation
Copy pubsubserverinfo.jar into the plugins directory of your Openfire installation. The plugin will then be
automatically deployed. To upgrade to a new version, copy the new pubsubserverinfo.jar file over the existing
file.

## Reporting Issues

Issues may be reported to the [forums](https://discourse.igniterealtime.org) or via this repo's [Github Issues](https://github.com/igniterealtime/openfire-pubsubserverinfo-plugin).
