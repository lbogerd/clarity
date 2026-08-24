# Clarity

Clarity is an open-source parser for Dota 2, CSGO, CS2 and Deadlock replay files, written in Java.

## Changelog
see the [Changelog](/CHANGELOG.md) for recent project activity.

# Replay Data

Clarity produces the following data you might be interested in from a replay. Choose from:

* **combat log**: a detailed log of events that happened in the game
* **entities**: in-game things like heroes, players, and creeps
* **modifiers**: auras and effects on in-game entities
* **temporary entities**: fire-and-forget things the game server tells the client about*
* **user messages**: many different things, including spectator clicks, global chat messages, overhead events (like last-hit gold, and much more), particle systems, etc.*
* **game events**: lower-level messages like Dota TV control (directed camera commands, for example), etc.*
* **voice data**: commentary in pro matches*
* **sounds**: sounds that occur in the game*
* **overview**: end-of-game summary, including players, game winner, match id, duration, and often picks/bans
* **unprocessed**: data is provided as original protobuf message object

# Requirements

* Java (17 and above)
* Gradle (for building)

# Usage

Depending on your project build, use one of the following

### Maven
```XML
<dependency>
	<groupId>com.skadistats</groupId>
	<artifactId>clarity</artifactId>
	<version>4.0.1</version>
</dependency>
```

### Gradle (Groovy)
```
    implementation group: 'com.skadistats', name: 'clarity', version: '4.0.1'
```

### Gradle (Kotlin)
```
    implementation("com.skadistats:clarity:4.0.1")
```

# Example Code

For example code, please see the separate project [clarity-examples](https://github.com/skadistats/clarity-examples).

## Run-time Listener Filters

`SimpleRunner` can filter the messages and entity classes delivered to catch-all listeners at run time. The existing `SimpleRunner(Source)` constructor uses `RunnerFilters.ALL`, so it still delivers all data.

The following example excludes one exact message type and two exact entity classes:

```java
Set<Class<? extends GeneratedMessage>> excludedMessages = Set.of(
    CommonNetworkBaseTypes.CNETMsg_Tick.class
);
Set<String> excludedEntityClasses = Set.of(
    "CDOTA_Unit_Hero_Axe",
    "CDOTA_Unit_Hero_Bane"
);

var filters = new RunnerFilters(
    messageClass -> !excludedMessages.contains(messageClass),
    dtClass -> !excludedEntityClasses.contains(dtClass.getDtName())
);

try (var source = new MappedFileSource(path)) {
    new SimpleRunner(source, filters).runWith(processor);
}
```

The message predicate applies only to catch-all `@OnMessage` and `@OnPostEmbeddedMessage` listeners. A listener with an explicit message type, such as `@OnMessage(CSVCMsg_PacketEntities.class)`, still receives that type. Clarity does not parse a nested user-message payload when no typed listener or accepted catch-all listener needs it.

The entity predicate applies only when an entity listener uses the default `classPattern = ".*"`. An explicit `classPattern` takes priority. Entity filters reduce listener callbacks, but do not stop entity decoding or state updates.

# License

See [LICENSE](/LICENSE) in the project root.
