# Skillz Example Sync Server

See the corresponding Unity Sync Client Example here: [skillz-example-sync-client](https://github.com/skillz/skillz-example-sync-client)

### Table of Contents
  - [Structure](#structure)
  - [Setting up a new project](#setting-up-a-new-project)
  - [Message Validation and Handling](#message-validation-and-handling)
  - [Adding Functionality](#adding-functionality)
  - [Sending New Messages](#sending-new-messages)
  - [Client-Side Send and Receive](#client-side-send-and-receive)
  - [Reconnecting Players](#reconnecting-players)
  - [Built-in Data and Functions](#built-in-data-and-functions)
  - [Reserved Opcodes](#reserved-opcodes)

The purpose of the Skillz Server SDK is to provide a stream-lined development environment for authoritative synchronous game servers. Our goal is to ensure that the base from which our developers are building is maintainable, upgradable, and consistent, and keeps developers focused on the content of the game.

We will continue to provide bug fixes, optimizations, access to new features, and further support to all of our developers through this SDK.

### Structure
We have separated out the Skillz Server logic into an obfuscated JAR file so that developers can focus on their custom game logic rather than looking at network code.

One of the goals is to provide a consistent implementation of features that are shared across all types of synchronous games. Things like connecting, keep alive, managing active matches, pause/resume, reconnecting, tracking turns, ensuring fair use of random numbers, and much more can be shared across almost all game types.

We are actively working on implementing new features in the SDK, but here is short list of what currently comes in the box:

* Connecting to the server and verifying that the Matchmaker match was authentic
* Login Queue to ensure bursts of connections are handled gracefully
* Reconnecting to the server after losing access to the internet and restoring player state
* Pausing/Resuming the game when players background/foreground the app
* Tracking users that have been inactive (disconnected or backgrounded) for too long
* Broadcast tick system for authoritative updates to players. Optional Passthrough mode if this is not desired
* TLS to ensure fairness through encrypted communications

Technologies
* Netty 4.1.42 backend
* Flatbuffers for message communication
* Groovy for dynamic scripting and MessageHandler reflection
* Gradle build management

### Setting up a new project

Requirements to follow our setup:
* Custom Flatbuffer compiler that simply extends our Message rather than Table to make use of opcodes 
    * Please copy the `flatc` executable binary file located in `third-party/flatc/` to `/usr/local/bin` and ensure it has proper permissions
* Intellij Idea IDE

Running the project:
* Use intellij to open a new project. Select the `build.gradle` file in the root directory of the project
* The project should automatically populate with Gradle tasks. If the window isn’t open, access it via view->Tool Windows->Gradle
* Under the root Gradle project, there is Tasks->application->run. Double click this to start the server locally

Additionally, relevant Gradle tasks include:
* Tasks->build->build (Build the project)
* Tasks->other->createFlatBuffersGroovy (uses flatc to auto generate groovy code representing each message)
* Tasks->other->createFlatBuffersCPP or createFlatBuffersCSharp (uses flatc to auto generate CPP or CSharp code meant for use with clients like Unity)

The Tick and Passthrough
The server can operate in one of two modes — TICK and PASSTHROUGH. Running the server in TICK mode is the preferred way of developing with this SDK and ensures that messages are processed on and sent out on each “tick” of the server. This tick rate is defined in milliseconds in your custom Game class like so:
```
public static final int TICK_RATE = 150
```
A Tick can be thought of in roughly the same way as the Frame Rate used in Game Engines. We have a fixed deltaTime that is the Tick Rate, and we have functions that are executed each Tick similar to the Update() function in Unity, which is executed each frame

The purpose of a Tick is to:
* Provide a consistent service SLA. The server will always respond at the tick rate interval, meaning that each broadcast remains consistent regardless of server load. This is useful for providing a consistent user experience and makes things like client-side interpolation of frames easier to implement
* Provide fair processing of client inputs. By setting a frame of reference, a Tick allows us to define an interval for which inputs are said to have occurred “at the same time”. When two inputs are considered to have been received at the same time (within the same Tick interval), then the server SDK will automatically handle selecting which user to process first using a fair system that trades off which user has priority

We highly recommend using a Tick, but if a Tick is undesirable, it can be turned off by starting the Server in PASSTHROUGH mode. In this mode, packets will be processed as soon as they are received and broadcast out as fast as possible.

With the TICK_RATE set to 150, the server will read the incoming messages every 150ms, process those messages, and respond at the start of the next tick. This ensures fairness as players have different latencies.

For example, imagine two players attempt to pick up an item at the same time. Each player sends an ItemPickupMessage but Player A is located physically closer to the game-server. As such, Player A’s message is received before Client B’s message, and they pick up the item, even though they clicked at the same time.

With a 150ms tick rate, the messages would be received during the same tick (during a 150ms time period), stored, and processed at the same time at the end of the tick. After processing the messages, any outgoing messages are sent. This ensures that the server is latency tolerant and player input is processed fairly.

If you run the server in PASSTHROUGH mode, then messages are instantly processed when received and instantly sent with the player.passthrough method. This can be useful for implementing features like chat/taunts, where no server logic is required and where there is no effect on fairness.

### Message Validation and Handling
Messages are processed in two parts.

First, the optional “validate” method is called. If you have this method implemented in your handler it is responsible for determining whether the message is valid or invalid. If the data in the message is valid then the function returns true and the “on” function is then called.

For example, here’s what it looks like to validate an example PlayerMove message such that a player isn’t allowed to move outside of the coordinates (-100, -100) to (100, 100). Now, instead of relying on the game client to keep the player within a certain bounds, we can be certain that the server will prevent improper movement.
```
def validate(PlayerMove message) {
    if (player.game.isGamePaused()) {
        return false
    }

    if (message.xMovement() + player.xPos() > 100 || message.xMovement() + player.xPos() < 0) {
        return false
    }

    if (message.yMovement() + player.yPos() > 100 || message.yMovement() + player.yPos() < 0) {
        return false
    }

    return true
}
```
If the packet is valid, we pass the message to the “on” function and actually process it, like so:
```
def on(PlayerMove message) {
    player.xPos += message.xMovement()
    player.yPos += message.yMovement()
}
```

### Adding Functionality
To start, let’s add a simple chat. Chat is initiated from the Client as a Client->Server message. This means we will use a MessageHandler to handle and respond to it.

* The first thing we do when adding a new piece of functionality is to define the Flatbuffer that will be used for communication
* Create a new Chat.fbs file in example_server->generated->flatbuffers->Chat.fbs
Add the following contents:
```
table Chat {
    opcode: short = 13;
    messageId: short;
}
```
* Execute the Gradle task createFlatBuffersGroovy to generate the Chat.groovy file located in example_server->generated->messages
* Create a new Handler class in example_server->src->com.name.game->ChatHandler.groovy
```
class ChatHandler extends MessageHandler<Player> {
    def validate(Chat message) {
        if (player.appPaused) {
            return false
        }
        if (message.messageId() > 10 || message.messageId() < 1) {
            return false
        }
        return true
    }
    
    def on(Chat message) {
        player.passthrough message
    }
}
```
Any class that extends MessageHandler<Player> will be scanned for handlers. In this case, we validate and handle the Chat message. The use of validate is optional, but its use is encouraged in order to be explicit about validation logic. In the above example, you can easily see that chat messages can’t be sent while the app is paused, and chat messageIDs must be within a certain range. This ensures that even a modified client that sends funky messageIds cannot do anything a normal client couldn’t do.

That’s it!

The server will now accept a FlatBuffer with opcode=13, and automatically validate it using the above function, and then finally pass the FlatBuffer through to the other player’s client. It is not up to the client to handle this.

Because we used the player.passthrough function to send the chat message, the server simply passes the byte[] that it received down to the other player.

But what if we wanted to control the timing of the display of these chat messages server-sided?

We could add a chatTimer in Player and set it on sending out a Chat Message. Then, in process(), we can decrement it by some amount every tick. We can include the current time remaining on this timer in our new version of the Chat Message:
```
table Chat {
    opcode: short = 13;
    messageTimer: short;
    messageId: short;
}
```
Now we can include a timer value which tells the client to hide the chat message once it reaches a value of 0. This message can be sent by building a new FlatBuffer and using the player.write function, as shown in more detail below. Finally we can decrement the timer value in player.process and send the new message out on each tick in Game.broadcast. Now the server is controlling the display time for the chat message rather than just passing it from one client to the other.

### Sending New Messages
In order to send the newly created Chat message, we need to build a FlatBuffer and use that to create a byte[] array which is sent down to the client. We use the MessageBuilder class to do this, as shown below. The FlatBuffer tutorial is very helpful in understanding how to create and build new messages: https://google.github.io/flatbuffers/flatbuffers_guide_tutorial.html
```
MessageSender sendChat(short messageId, short chatTimeRemaining) {
    MessageBuilder builder = new MessageBuilder()

    Chat.startChat(builder)
    Chat.addOpcode(builder, new Chat().opcode())
    Chat.addMessageTimer(builder, chatTimeRemaining)
    Chat.addMessageId(builder, messageId)

    int offset = Chat.endChat(builder)
    builder.finish(offset)
    player.write(builder.sizedByteArray())

    this
}
```
It may be useful for organizational purposes to store these message sender functions in one class, such as MessageSender, and to have each function return the class instance such that you can chain function calls (ex. player.messageSender.sendChat().sendItem())

Let’s take a look at a more complicated example. This is how the Server SDK builds and sends down the MatchSuccess packet once both players have connected to the server. Notice how the non-primitive data types (the two strings) are created using the MessageBuilder object and stored as integer offsets. We then pass these integers to the addVariable functions to store the data in the FlatBuffer.
```
MessageSender sendMatchSuccess() {
    MessageBuilder builder = new MessageBuilder()

    int registeredMatchIdOffset = builder.createString(getMatchId())
    int serverVersionStringOffset = builder.createString(Game.VERSION)

    MatchSuccess.startMatchSuccess(builder)
    MatchSuccess.addOpcode(builder, (short) new MatchSuccess().opcode())
    MatchSuccess.addRegisteredMatchId(builder, registeredMatchIdOffset)
    MatchSuccess.addTickRate(builder, Tick.RATE)
    MatchSuccess.addOpponentUserId(builder, game.getOtherPlayer(this).getUserId())

    MatchSuccess.addServerVersion(builder, serverVersionStringOffset)
    MatchSuccess.addServerVersionCode(builder, (short)Game.VERSION_NUMBER)

    int successPacketOffset = MatchSuccess.endMatchSuccess(builder)
    builder.finish(successPacketOffset)

    write(builder.sizedByteArray())
    this
}
```
### Client-Side Send and Receive
But what about on the client? How do we handle sending and receiving data from inside of Unity?

The SyncClient class handles connecting, reconnecting, sending, and receiving data to/from the server. During local development, the connection settings (IP, Port, etc) can be configured using the assets in the SyncClient folder in the main Assets folder.

Let’s take a look at how we’d send that same chat message using C# and our example unity project. First we need to create a function to build the FlatBuffer and create the byte[] array, just as in the Java server. This will look nearly identical to the Java function above, excepting that we pull the Opcode from an enum class rather than from a new instance of the message. In the example project, these functions are located in the PacketFactory class.
```
public static byte[] MakeChatBuffer(short messageId)
{
    var builder = new MessageBuilder();
    
    Chat.StartChat(builder);
    Chat.AddOpcode(builder, (sbyte)Opcode.Chat);
    Chat.AddMessageId(builder, messageId);
    var offset = ForfeitMatch.EndForfeitMatch(builder);
    
    builder.Finish(offset.Value);
    return builder.SizedByteArray();
}
```
As for receiving messages, that’s handled by the SyncClient and SyncGameController classes. When the SyncClient receives messages, it inserts them into a queue to be read from by SyncGameController’s Update loop.

Inside of Update, we loop and pull out each byte[] array in the queue. We then create a generic Packet object from that byte array. The Packet FlatBuffer is a very simple message that only contains an opcode value. We can use this newly created Packet message to determine the incoming opcode and route it to the right handler function using a switch statement.
```
private void Update()
{
    byte[] data;
    while (client.GetNextPacket(out data))
    {
        if (UserData.Instance.IsGameOver)
        {
            return;
        }

        var packet = PacketFactory.BytesToPacket(data);
        var byteBuffer = new ByteBuffer(data);

        switch ((Opcode)packet.Opcode)
        {
            case Opcode.MatchSuccess:
                client.ResetReadTimer();
                on(MatchSuccess.GetRootAsMatchSuccess(byteBuffer));

                client.SetReadTimeout(2000);
                break;

            case Opcode.GameState:
                on(GameState.GetRootAsGameState(byteBuffer));
                break;

            case Opcode.MatchOver:
                on(MatchOver.GetRootAsMatchOver(byteBuffer));
                break;

            case Opcode.OpponentConnectionStatus:
                on(OpponentConnectionStatus.GetRootAsOpponentConnectionStatus(byteBuffer));
                break;

            case Opcode.PlayerReconnected:
                on(PlayerReconnected.GetRootAsPlayerReconnected(byteBuffer));
                break;

            case Opcode.OpponentPaused:
                on(OpponentPaused.GetRootAsOpponentPaused(byteBuffer));
                break;

            case Opcode.OpponentResumed:
                on(OpponentResumed.GetRootAsOpponentResumed(byteBuffer));
                break;

            default:
                Debug.Log("SyncGameController: Received packet with unimplemented/unsupported authcode: " + packet.Opcode);
                break;
        }
    }
}
```
Once we have determined the correct opcode, we create the corresponding Message object using the associated getRootAs function and original incoming ByteBuffer and pass that to a handler function. For example, to process a GameState message, we create a GameState object using GameState.GetRootAsGameState(byteBuffer) and pass that to its handler function, as seen below. Here we can directly access the data in the message and update the game.
```
private void on(GameState message)
{
    tickCount = (int)message.TickCount;
    
    matchInfoDisplay.PlayerScore = message.PlayerScore;
    matchInfoDisplay.OpponentScore = message.OpponentScore;
    matchInfoDisplay.CurrentGameTick = message.GameTickCount;
    matchInfoDisplay.CurrentTick = message.TickCount;
}
```
### Reconnecting Players
If a player is disconnected during an in-progress game, the client will recognize this and attempt to reconnect while the server pauses the game. Upon reconnect, the server will create a new player object and replace the old player object with it. While doing so, any built-in data like score, number of reconnects, etc are copied from the old object to the new object automatically.

However, any custom variables that you add to your Player class must be manually copied over by overriding the reconnect method in Player, as shown below. In this example, we are copying the custom values “weaponId” and “health”.
```
@Override
def reconnect(Client existingPlayer) {
    // Ran when a player reconnects and used to restore the state from the originally connected player
    existingPlayer = existingPlayer as Player
    this.weaponId = existingPlayer.weaponId
    this.health = existingPlayer.health
}
```

### Built-in Data and Functions
* Player
    * Variables
        * int score
        * FairRandom fairRandom
        * long userId
        * ConnectionStatus connectionStatus
        * HashMap<String, Float> gameParams
    * Functions
        * Game getGame()
        * String getMatchId()
        * void start()
        * void process()
        * void reconnect(Client existingPlayer)
        * int nextInt()
        * void forfeit()
        * void pauseGame()
        * void resumeGame()
* Game
    * Variables
        * String VERSION
        * int VERSION_NUMBER
    * Functions
        * void start()
        * void process()
        * void broadcast()
        * void onGameOver()
        * void setCompleted()
        * List< P > getPlayers()
        * World getWorld()
        * FairRandom getFairRandom()
        * Random getRandom()
        * boolean isGamePaused()
        * P getOtherPlayer(P thePlayer)
        * int getTickCounter()
        * int getGameTickCounter()
        * int getPauseTickCounter()
        * boolean isForfeited()

### Reserved Opcodes
These opcodes are in use by the Server SDK and should not be used by the developer for any new messages:
* 0  => Invalid
* 1  => Connect
* 2  => KeepAlive
* 3  => Forfeit
* 4  => AppPaused
* 5  => AppResumed
* 6  => MatchSuccess
* 7  => OpponentPaused
* 8  => OpponentResumed
* 9  => OpponentConnectionStatus
* 10 => PlayerReconnected
* 11 => MatchOver
