package com.semaphore_soft.apps.cypher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.Pair;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.semaphore_soft.apps.cypher.game.Actor;
import com.semaphore_soft.apps.cypher.game.ActorController;
import com.semaphore_soft.apps.cypher.game.Entity;
import com.semaphore_soft.apps.cypher.game.GameController;
import com.semaphore_soft.apps.cypher.game.GameMaster;
import com.semaphore_soft.apps.cypher.game.Item;
import com.semaphore_soft.apps.cypher.game.ItemConsumable;
import com.semaphore_soft.apps.cypher.game.Model;
import com.semaphore_soft.apps.cypher.game.Room;
import com.semaphore_soft.apps.cypher.game.Special;
import com.semaphore_soft.apps.cypher.networking.NetworkConstants;
import com.semaphore_soft.apps.cypher.networking.ResponseReceiver;
import com.semaphore_soft.apps.cypher.networking.Server;
import com.semaphore_soft.apps.cypher.networking.ServerService;
import com.semaphore_soft.apps.cypher.opengl.ARRoom;
import com.semaphore_soft.apps.cypher.ui.UIListener;
import com.semaphore_soft.apps.cypher.ui.UIPortalActivity;
import com.semaphore_soft.apps.cypher.ui.UIPortalOverlay;
import com.semaphore_soft.apps.cypher.utils.CollectionManager;
import com.semaphore_soft.apps.cypher.utils.GameStatLoader;
import com.semaphore_soft.apps.cypher.utils.Logger;

import org.artoolkit.ar.base.ARActivity;
import org.artoolkit.ar.base.rendering.ARRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import static com.semaphore_soft.apps.cypher.utils.CollectionManager.getNextID;

/**
 * @author scorple
 */

public class PortalActivity extends ARActivity implements PortalRenderer.NewMarkerListener,
                                                          ResponseReceiver.Receiver,
                                                          UIListener,
                                                          GameController
{
    private UIPortalActivity uiPortalActivity;
    private UIPortalOverlay  uiPortalOverlay;

    private PortalRenderer renderer;

    private static ResponseReceiver responseReceiver;
    private static ServerService    serverService;
    private static boolean mServerBound  = false;
    private static boolean sendHeartbeat = true;
    private static Handler handler       = new Handler();

    private static int    playerId;
    private static String characterName;

    private static final Model model = new Model();

    private static boolean turn;
    private static int     turnId;

    private static ArrayList<Integer> reservedMarkers;

    private static HashMap<Integer, String> playerCharacterMap;

    private static boolean playerMarkerSelected = false;

    private static int numClients;
    private static int numClientsSelected = 0;

    private static boolean winCondition  = false;
    private static boolean loseCondition = false;

    // Runnable to send heartbeat signal to clients
    private Runnable heartbeat = new Runnable()
    {
        @Override
        public void run()
        {
            if (sendHeartbeat)
            {
                serverService.writeAll(NetworkConstants.GAME_HEARTBEAT);
                handler.postDelayed(heartbeat, NetworkConstants.HEARTBEAT_DELAY);
            }
        }
    };

    private static MediaPlayer mediaPlayer;

    private static boolean musicEnabled = true;

    /**
     * {@inheritDoc}
     *
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState); //Calls ARActivity's actor, abstract class of ARBaseLib

        mediaPlayer = MediaPlayer.create(this, R.raw.overworld);
        mediaPlayer.setLooping(true);
        mediaPlayer.start();

        setContentView(R.layout.empty);

        // Setup ui
        uiPortalActivity = new UIPortalActivity(this);
        ((FrameLayout) this.findViewById(R.id.empty)).addView(uiPortalActivity);
        uiPortalActivity.setUIListener(this);

        uiPortalOverlay = new UIPortalOverlay(this);
        ((FrameLayout) this.findViewById(R.id.overlay_frame)).addView(uiPortalOverlay);
        uiPortalOverlay.setUIListener(this);

        // Setup AR 3d graphics
        renderer = new PortalRenderer();
        renderer.setContext(this);
        PortalRenderer.setGameController(this);
        PortalRenderer.setNewMarkerListener(this);

        // Setup broadcast networking service broadcast receiver
        responseReceiver = new ResponseReceiver();
        responseReceiver.setListener(this);
        LocalBroadcastManager.getInstance(this)
                             .registerReceiver(responseReceiver, NetworkConstants.getFilter());

        playerId = 0;
        characterName = getIntent().getStringExtra("character");

        PortalRenderer.setHandler(handler);

        turn = true;
        turnId = 0;

        reservedMarkers = new ArrayList<>();

        playerCharacterMap = new HashMap<>();
        playerCharacterMap.put(playerId, characterName);

        numClients = getIntent().getExtras().getInt("num_clients", 0);

        playerCharacterMap =
            (HashMap<Integer, String>) getIntent().getSerializableExtra("character_selection");

        uiPortalOverlay.setCharPortrait(characterName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStart()
    {
        super.onStart();
        // Bind to ServerService
        Intent intent = new Intent(this, ServerService.class);
        bindService(intent, mServerConnection, Context.BIND_AUTO_CREATE);
        handler.postDelayed(heartbeat, NetworkConstants.HEARTBEAT_DELAY);
        // Make sure we are able to reconnect if we were in the background
        Server.setReconnect(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop()
    {
        super.onStop();
        // Don't reconnect while we are in the background
        Server.setReconnect(false);
        // Unbind from the service
        if (mServerBound)
        {
            unbindService(mServerConnection);
            mServerBound = false;
        }
        sendHeartbeat = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBackPressed()
    {
        //do nothing
    }

    /**
     * {@inheritDoc}
     *
     * @return
     */
    //pass our rendering program to the ar framework
    @Override
    protected ARRenderer supplyRenderer()
    {
        return renderer;
    }

    /**
     * {@inheritDoc}
     *
     * @return
     */
    //pass the the frame to draw the camera feed and
    //the ar graphics within to the ar framework
    @Override
    protected FrameLayout supplyFrameLayout()
    {
        return (FrameLayout) this.findViewById(R.id.portal_frame);
    }

    /**
     * {@inheritDoc}
     *
     * @param cmd Command from UI interaction
     */
    @Override
    public void onCommand(final String cmd)
    {
        Logger.logI("portal activity received command: " + cmd, 3);

        if (cmd.startsWith("cmd_btn"))
        {
            switch (cmd)
            {
                case "cmd_btnPlayerMarkerSelect":
                    int firstUnreservedMarker = getFirstUnreservedMarker();
                    if (firstUnreservedMarker > -1 &&
                        selectPlayerMarker(playerId, characterName, firstUnreservedMarker))
                    {
                        renderer.setPlayerMarker(firstUnreservedMarker);

                        Toast.makeText(getApplicationContext(),
                                       "Player Marker Set",
                                       Toast.LENGTH_SHORT)
                             .show();

                        serverService.writeAll(
                            NetworkConstants.PREFIX_RESERVE_PLAYER + firstUnreservedMarker);

                        int markCharId = 0;

                        switch (characterName)
                        {
                            case NetworkConstants.GAME_KNIGHT:
                                markCharId = 0;
                                break;
                            case NetworkConstants.GAME_SOLDIER:
                                markCharId = 1;
                                break;
                            case NetworkConstants.GAME_RANGER:
                                markCharId = 2;
                                break;
                            case NetworkConstants.GAME_WIZARD:
                                markCharId = 3;
                                break;
                        }

                        serverService.writeAll(NetworkConstants.PREFIX_ATTACH + markCharId + ":" +
                                               firstUnreservedMarker);

                        playerMarkerSelected = true;

                        if (numClientsSelected == numClients)
                        {
                            uiPortalOverlay.overlayStartMarkerSelect();
                        }
                        else
                        {
                            uiPortalOverlay.overlayWaitingForClients();
                        }
                    }
                    break;
                case "cmd_btnStartMarkerSelect":
                    if (selectStartMarker())
                    {
                        Actor actor = GameMaster.getActor(model, playerId);

                        if (actor != null)
                        {
                            uiPortalOverlay.overlayAction(actor.getHealthMaximum(),
                                                          actor.getHealthCurrent(),
                                                          actor.getSpecialMaximum(),
                                                          actor.getSpecialCurrent());
                        }
                        else
                        {
                            uiPortalOverlay.overlayAction(1, 0, 1, 0);
                        }

                        for (int id : GameMaster.getPlayerActorIds(model))
                        {
                            if (id != playerId)
                            {
                                Actor clientActor = GameMaster.getActor(model, id);
                                if (clientActor != null)
                                {
                                    serverService.writeToClient(NetworkConstants.PREFIX_START +
                                                                clientActor.getHealthMaximum() +
                                                                ":" +
                                                                clientActor.getHealthCurrent() +
                                                                ":" +
                                                                clientActor.getSpecialMaximum() +
                                                                ":" +
                                                                clientActor.getSpecialCurrent(),
                                                                id);
                                }
                                else
                                {
                                    serverService.writeToClient(
                                        NetworkConstants.PREFIX_START + 1 + ":" + 0 + ":" + 1 +
                                        ":" + 0, id);
                                }
                            }
                        }

                        PortalRenderer.setLookingForNewMarkers(true);
                    }
                    break;
                case "cmd_btnOpenDoor":
                    if (openDoor())
                    {
                        renderer.setCheckingNearestRoomMarker(false);

                        Actor actor = GameMaster.getActor(model, playerId);

                        if (actor != null)
                        {
                            int proposedRoomId = actor.getProposedRoomId();

                            Room room = GameMaster.getRoom(model,
                                                           proposedRoomId >
                                                           1 ? proposedRoomId : actor.getRoom());

                            if (proposedRoomId > -1)
                            {
                                GameMaster.moveActor(model, playerId, proposedRoomId);
                            }

                            Toast.makeText(getApplicationContext(),
                                           "You opened a door",
                                           Toast.LENGTH_SHORT).show();
                            serverService.writeAll(
                                NetworkConstants.PREFIX_FEEDBACK + actor.getDisplayName() +
                                " opened a door");

                            String desc = null;

                            if (room != null)
                            {
                                showAction(room.getMarker(),
                                           playerId,
                                           -1,
                                           1000,
                                           "door",
                                           null,
                                           true,
                                           false,
                                           desc);
                            }
                        }

                        if (actor != null)
                        {
                            uiPortalOverlay.overlayWaitingForTurn(actor.getHealthMaximum(),
                                                                  actor.getHealthCurrent(),
                                                                  actor.getSpecialMaximum(),
                                                                  actor.getSpecialCurrent());
                        }
                        else
                        {
                            uiPortalOverlay.overlayWaitingForTurn(1, 0, 1, 0);
                        }
                    }
                    /*else
                    {
                        Toast.makeText(getApplicationContext(),
                                       "You can't open a door here",
                                       Toast.LENGTH_SHORT).show();
                    }*/
                    break;
                case "cmd_btnAttack":
                    ArrayList<Pair<String, String>> attackOptions = new ArrayList<>();

                    for (int i : GameMaster.getNonPlayerTargetIds(model, playerId))
                    {
                        Actor target = GameMaster.getActor(model, i);
                        if (target != null)
                        {
                            Pair<String, String> targetPair =
                                new Pair<>(target.getDisplayName(),
                                           "cmd_attack:" + i);

                            attackOptions.add(targetPair);
                        }
                    }

                    uiPortalOverlay.overlaySelect(attackOptions, false, false);
                    break;
                case "cmd_btnDefend":
                {
                    renderer.setCheckingNearestRoomMarker(false);

                    GameMaster.setActorState(model, playerId, Actor.E_STATE.DEFEND);

                    Actor actor = GameMaster.getActor(model, playerId);

                    if (actor != null)
                    {
                        int proposedRoomId = actor.getProposedRoomId();

                        Room room = GameMaster.getRoom(model,
                                                       proposedRoomId >
                                                       1 ? proposedRoomId : actor.getRoom());

                        if (proposedRoomId > -1)
                        {
                            GameMaster.moveActor(model, playerId, proposedRoomId);
                        }

                        Toast.makeText(getApplicationContext(),
                                       "You defended",
                                       Toast.LENGTH_SHORT).show();
                        serverService.writeAll(
                            NetworkConstants.PREFIX_FEEDBACK + actor.getDisplayName() +
                            " defended");

                        String desc = actor.getName();

                        if (room != null)
                        {
                            showAction(room.getMarker(),
                                       playerId,
                                       -1,
                                       1000,
                                       "defend",
                                       null,
                                       true,
                                       false,
                                       desc);
                        }
                    }

                    if (actor != null)
                    {
                        uiPortalOverlay.overlayWaitingForTurn(actor.getHealthMaximum(),
                                                              actor.getHealthCurrent(),
                                                              actor.getSpecialMaximum(),
                                                              actor.getSpecialCurrent());
                    }
                    else
                    {
                        uiPortalOverlay.overlayWaitingForTurn(1, 0, 1, 0);
                    }

                    break;
                }
                case "cmd_btnSpecial":
                    ArrayList<Pair<String, String>> specialOptions = new ArrayList<>();

                    for (Special special : GameMaster.getSpecials(model, playerId).values())
                    {
                        Pair<String, String> specialPair =
                            new Pair<>(special.getDisplayName(), "cmd_special:" + special.getId());

                        specialOptions.add(specialPair);
                    }

                    uiPortalOverlay.overlaySelect(specialOptions, false, true);
                    break;
                case "cmd_btnCancel":
                {
                    Actor actor = GameMaster.getActor(model, playerId);

                    if (actor != null)
                    {
                        uiPortalOverlay.overlayAction(actor.getHealthMaximum(),
                                                      actor.getHealthCurrent(),
                                                      actor.getSpecialMaximum(),
                                                      actor.getSpecialCurrent());
                    }
                    else
                    {
                        uiPortalOverlay.overlayAction(1, 0, 1, 0);
                    }

                    break;
                }
                case "cmd_btnItems":
                {
                    ArrayList<Pair<String, String>> options = new ArrayList<>();

                    Pair<String, String> inventory = new Pair<>("Inventory", "cmd_btnInventory");
                    options.add(inventory);

                    Pair<String, String> floor = new Pair<>("Floor", "cmd_btnFloor");
                    options.add(floor);

                    uiPortalOverlay.overlaySelect(options, true, true);
                    break;
                }
                case "cmd_btnInventory":
                {
                    ArrayList<Pair<String, String>> options = new ArrayList<>();

                    Actor actor = GameMaster.getActor(model, playerId);

                    if (actor != null)
                    {
                        for (Item item : actor.getItems().values())
                        {
                            Pair<String, String> itemPair =
                                new Pair<>(item.getDisplayName(), "cmd_invItem:" + item.getId());
                            options.add(itemPair);
                        }
                    }

                    uiPortalOverlay.overlaySelect(options, true, true);
                    break;
                }
                case "cmd_btnFloor":
                {
                    ArrayList<Pair<String, String>> options = new ArrayList<>();

                    Actor actor = GameMaster.getActor(model, playerId);
                    Room  room  = null;

                    if (actor != null && actor.getProposedRoomId() != -1)
                    {
                        GameMaster.getRoom(model, actor.getProposedRoomId());
                    }
                    else
                    {
                        room = GameMaster.getActorRoom(model, playerId);
                    }

                    if (room != null)
                    {
                        for (int itemId : room.getResidentItems())
                        {
                            Item item = GameMaster.getItem(model, itemId);

                            if (item != null)
                            {
                                Pair<String, String> itemPair =
                                    new Pair<>(item.getDisplayName(),
                                               "cmd_floorItem:" + item.getId());
                                options.add(itemPair);
                            }
                        }
                    }

                    uiPortalOverlay.overlaySelect(options, true, true);
                    break;
                }
                case "cmd_btnToggleMusic":
                    toggleMusic();
                    break;
                case "cmd_btnToggleSound":
                    renderer.toggleSound();
                    break;
                default:
                    break;
            }
        }
        else
        {
            String[] splitCmd    = cmd.split("_");
            String[] splitAction = splitCmd[1].split(":");

            if (splitAction[0].equals("attack"))
            {
                int targetId = Integer.parseInt(splitAction[1]);
                attack(playerId, targetId);
            }
            else if (splitAction[0].equals("special"))
            {
                int specialId = Integer.parseInt(splitAction[1]);

                Special.E_TARGETING_TYPE specialType =
                    GameMaster.getSpecialTargetingType(model, specialId);

                if (splitAction.length < 3)
                {
                    if (specialType == Special.E_TARGETING_TYPE.AOE_PLAYER || specialType ==
                                                                              Special.E_TARGETING_TYPE.AOE_NON_PLAYER)
                    {
                        performSpecial(playerId, specialId);
                    }
                    else
                    {
                        ArrayList<Pair<String, String>> targetOptions = new ArrayList<>();

                        if (specialType == Special.E_TARGETING_TYPE.SINGLE_NON_PLAYER)
                        {
                            for (Actor target : GameMaster.getNonPlayerTargets(model, playerId)
                                                          .values())
                            {
                                if (target != null)
                                {
                                    Pair<String, String> targetPair =
                                        new Pair<>(target.getDisplayName(),
                                                   "cmd_special:" + specialId + ":" +
                                                   target.getId());

                                    targetOptions.add(targetPair);
                                }
                            }
                        }
                        else
                        {
                            for (Actor target : GameMaster.getPlayerTargets(model, playerId)
                                                          .values())
                            {
                                if (target != null)
                                {
                                    Pair<String, String> targetPair =
                                        new Pair<>(target.getDisplayName(),
                                                   "cmd_special:" + specialId + ":" +
                                                   target.getId());

                                    targetOptions.add(targetPair);
                                }
                            }
                        }

                        uiPortalOverlay.overlaySelect(targetOptions, false, true);
                    }
                }
                else
                {
                    performSpecial(playerId, Integer.parseInt(splitAction[2]), specialId);
                }
            }
            else if (splitAction[0].equals("invItem"))
            {
                ArrayList<Pair<String, String>> options = new ArrayList<>();

                Item item = GameMaster.getItem(model, Integer.parseInt(splitAction[1]));

                if (item instanceof ItemConsumable)
                {
                    Pair<String, String> useOption =
                        new Pair<>("Use", "cmd_useItem:" + splitAction[1]);
                    options.add(useOption);
                }

                Pair<String, String> dropOption =
                    new Pair<>("Drop", "cmd_dropItem:" + splitAction[1]);
                options.add(dropOption);

                uiPortalOverlay.overlaySelect(options, true, true);
            }
            else if (splitAction[0].equals("floorItem"))
            {
                ArrayList<Pair<String, String>> options = new ArrayList<>();

                Pair<String, String> takeOption =
                    new Pair<>("Take", "cmd_takeItem:" + splitAction[1]);
                options.add(takeOption);

                uiPortalOverlay.overlaySelect(options, true, true);
            }
            else if (splitAction[0].equals("useItem"))
            {
                boolean finished = false;

                Actor actor = GameMaster.getActor(model, playerId);
                Item  item  = GameMaster.getItem(model, Integer.parseInt(splitAction[1]));

                String type = null;

                if (actor != null && item != null && item instanceof ItemConsumable)
                {
                    switch (((ItemConsumable) item).getTargetingType())
                    {
                        case SINGLE_PLAYER:
                            type = ".help";
                            if (splitAction.length < 3)
                            {
                                ArrayList<Pair<String, String>> targetOptions = new ArrayList<>();

                                for (Actor target : GameMaster.getPlayerTargets(model, playerId)
                                                              .values())
                                {
                                    if (target != null)
                                    {
                                        Pair<String, String> targetPair =
                                            new Pair<>(target.getDisplayName(),
                                                       "cmd_useItem:" + splitAction[1] + ":" +
                                                       target.getId());

                                        targetOptions.add(targetPair);
                                    }
                                }

                                uiPortalOverlay.overlaySelect(targetOptions, false, true);
                            }
                            else
                            {
                                int   targetId    = Integer.parseInt(splitAction[2]);
                                Actor targetActor = GameMaster.getActor(model, targetId);

                                if (targetActor != null)
                                {
                                    if (targetActor.useItem(item))
                                    {
                                        actor.removeItem(Integer.parseInt(splitAction[1]));
                                        GameMaster.removeItem(model,
                                                              Integer.parseInt(splitAction[1]));

                                        Toast.makeText(getApplicationContext(),
                                                       "You used " + item.getDisplayName() +
                                                       " on " +
                                                       ((targetId ==
                                                         playerId) ? "yourself" : targetActor.getDisplayName()),
                                                       Toast.LENGTH_SHORT).show();
                                        serverService.writeAll(
                                            NetworkConstants.PREFIX_FEEDBACK +
                                            actor.getDisplayName() +
                                            " used " +
                                            item.getDisplayName());

                                        finished = true;
                                    }
                                }
                            }
                            break;
                        case SINGLE_NON_PLAYER:
                            type = ".harm";
                            if (splitAction.length < 3)
                            {
                                ArrayList<Pair<String, String>> targetOptions = new ArrayList<>();

                                for (Actor target : GameMaster.getNonPlayerTargets(model, playerId)
                                                              .values())
                                {
                                    if (target != null)
                                    {
                                        Pair<String, String> targetPair =
                                            new Pair<>(target.getDisplayName(),
                                                       "cmd_useItem:" + splitAction[1] + ":" +
                                                       target.getId());

                                        targetOptions.add(targetPair);
                                    }
                                }

                                uiPortalOverlay.overlaySelect(targetOptions, false, true);
                            }
                            else
                            {
                                int   targetId    = Integer.parseInt(splitAction[2]);
                                Actor targetActor = GameMaster.getActor(model, targetId);

                                if (targetActor != null)
                                {
                                    if (targetActor.useItem(item))
                                    {
                                        actor.removeItem(Integer.parseInt(splitAction[1]));
                                        GameMaster.removeItem(model,
                                                              Integer.parseInt(splitAction[1]));

                                        Toast.makeText(getApplicationContext(),
                                                       "You used " + item.getDisplayName() +
                                                       " on " + targetActor.getDisplayName(),
                                                       Toast.LENGTH_SHORT).show();
                                        serverService.writeAll(
                                            NetworkConstants.PREFIX_FEEDBACK +
                                            actor.getDisplayName() +
                                            " used " +
                                            item.getDisplayName() + " on " +
                                            targetActor.getDisplayName());

                                        finished = true;
                                    }
                                }
                            }
                            break;
                        case AOE_PLAYER:
                            type = ".help";
                            for (Actor target : GameMaster.getPlayerTargets(model, playerId)
                                                          .values())
                            {
                                target.useItem(item);

                                Toast.makeText(getApplicationContext(),
                                               "You used " + item.getDisplayName(),
                                               Toast.LENGTH_SHORT).show();
                                serverService.writeAll(
                                    NetworkConstants.PREFIX_FEEDBACK + actor.getDisplayName() +
                                    " used " +
                                    item.getDisplayName());

                                finished = true;
                            }
                            break;
                        case AOE_NON_PLAYER:
                            type = ".harm";
                            for (Actor target : GameMaster.getNonPlayerTargets(model, playerId)
                                                          .values())
                            {
                                target.useItem(item);

                                Toast.makeText(getApplicationContext(),
                                               "You used " + item.getDisplayName(),
                                               Toast.LENGTH_SHORT).show();
                                serverService.writeAll(
                                    NetworkConstants.PREFIX_FEEDBACK + actor.getDisplayName() +
                                    " used " +
                                    item.getDisplayName());

                                finished = true;
                            }
                            break;
                    }
                }
                else
                {
                    return;
                }

                if (finished)
                {
                    GameMaster.setActorState(model, playerId, Actor.E_STATE.NEUTRAL);

                    int proposedRoomId = actor.getProposedRoomId();

                    Room room = GameMaster.getRoom(model,
                                                   proposedRoomId >
                                                   1 ? proposedRoomId : actor.getRoom());

                    if (proposedRoomId > -1)
                    {
                        GameMaster.moveActor(model, playerId, proposedRoomId);
                    }

                    String desc = item.getName();

                    if (room != null)
                    {
                        showAction(room.getMarker(),
                                   playerId,
                                   -1,
                                   1000,
                                   "item" + type,
                                   null,
                                   true,
                                   false,
                                   desc);
                    }

                    uiPortalOverlay.overlayWaitingForTurn(actor.getHealthMaximum(),
                                                          actor.getHealthCurrent(),
                                                          actor.getSpecialMaximum(),
                                                          actor.getSpecialCurrent());
                }
            }
            else if (splitAction[0].equals("dropItem"))
            {
                Actor actor = GameMaster.getActor(model, playerId);
                Room  room  = GameMaster.getActorRoom(model, playerId);
                Item  item  = GameMaster.getItem(model, Integer.parseInt(splitAction[1]));

                if (room != null)
                {
                    if (room.getResidentItems().size() < 3)
                    {
                        room.addItem(Integer.parseInt(splitAction[1]));
                    }
                    else
                    {
                        Toast.makeText(this,
                                       "There's no room to drop an item here!",
                                       Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                if (actor != null)
                {
                    actor.removeItem(Integer.parseInt(splitAction[1]));
                }

                Toast.makeText(this,
                               "You dropped item " +
                               ((item != null) ? item.getDisplayName() : "an item"),
                               Toast.LENGTH_SHORT).show();
                serverService.writeAll(
                    ((actor != null) ? actor.getDisplayName() : "Someone") + " dropped " +
                    ((item != null) ? item.getDisplayName() : "an item"));

                if (actor != null && actor.getItems().size() > 0)
                {
                    onCommand("cmd_btnInventory");
                }
                else
                {
                    onCommand("cmd_btnCancel");
                }
            }
            else if (splitAction[0].equals("takeItem"))
            {
                Actor actor = GameMaster.getActor(model, playerId);
                Room  room  = GameMaster.getActorRoom(model, playerId);
                Item  item  = GameMaster.getItem(model, Integer.parseInt(splitAction[1]));

                if (actor != null && actor.getItems().size() >= 3)
                {
                    Toast.makeText(this, "Couldn't take item, bag is full", Toast.LENGTH_SHORT)
                         .show();

                    return;
                }

                if (actor != null && item != null)
                {
                    actor.addItem(item);
                }

                if (room != null && item != null)
                {
                    room.removeItem(item.getId());
                }

                if (item != null)
                {
                    Toast.makeText(this, "Took item " + item.getDisplayName(), Toast.LENGTH_SHORT)
                         .show();
                }

                if (room != null && room.getResidentItems().size() > 0)
                {
                    onCommand("cmd_btnFloor");
                }
                else
                {
                    onCommand("cmd_btnCancel");
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param msg      Message read from network
     * @param readFrom Device that message was received from
     */
    @Override
    public void handleRead(final String msg, final int readFrom)
    {
        if (msg.startsWith(NetworkConstants.PREFIX_MARK_REQUEST))
        {
            // Expect MarkerID of a marker that the client wants to attach to
            String[] splitMsg = msg.split(":");

            int mark = Integer.parseInt(splitMsg[1]);

            if (selectPlayerMarker(readFrom,
                                   playerCharacterMap.get(readFrom),
                                   mark))
            {
                serverService.writeToClient(NetworkConstants.GAME_WAIT, readFrom);
                serverService.writeToClient(NetworkConstants.PREFIX_ASSIGN_MARK + mark, readFrom);
                serverService.writeAll(NetworkConstants.PREFIX_RESERVE_PLAYER + splitMsg[1]);

                String characterName = playerCharacterMap.get(readFrom);

                int markCharId = 0;

                switch (characterName)
                {
                    case NetworkConstants.GAME_KNIGHT:
                        markCharId = 0;
                        break;
                    case NetworkConstants.GAME_SOLDIER:
                        markCharId = 1;
                        break;
                    case NetworkConstants.GAME_RANGER:
                        markCharId = 2;
                        break;
                    case NetworkConstants.GAME_WIZARD:
                        markCharId = 3;
                        break;
                }

                serverService.writeAll(
                    NetworkConstants.PREFIX_ATTACH + markCharId + ":" + splitMsg[1]);

                ++numClientsSelected;

                if (numClientsSelected == numClients && playerMarkerSelected)
                {
                    uiPortalOverlay.overlayStartMarkerSelect();
                }
            }
        }
        else if (msg.startsWith(NetworkConstants.PREFIX_GENERATE_ROOM_REQUEST))
        {
            // Expect MarkerID of a marker that the client wants generate a room on
            String[] splitMsg = msg.split(":");

            int mark = Integer.parseInt(splitMsg[1]);

            generateRoom(mark);
        }
        else if (msg.startsWith(NetworkConstants.PREFIX_OPEN_DOOR_REQUEST))
        {
            // Expect nearestMarkerID,
            // sideOfStartRoom,
            // sideOfEndRoom
            String[] splitMsg = msg.split(":");

            Actor actor = GameMaster.getActor(model, readFrom);

            int startRoomId = (actor != null &&
                               actor.getProposedRoomId() !=
                               -1) ? actor.getProposedRoomId() : GameMaster
                                  .getActorRoomId(model, readFrom);

            int endRoomId = GameMaster.getRoomIdByMarkerId(model, Integer.parseInt(splitMsg[1]));

            int res =
                GameMaster.openDoor(model,
                                    startRoomId,
                                    endRoomId,
                                    Short.parseShort(splitMsg[2]),
                                    Short.parseShort(splitMsg[3]));

            postOpenDoorResult(endRoomId, res, readFrom);

            if (res >= 0)
            {
                serverService.writeAll(NetworkConstants.PREFIX_PLACE_ROOM + splitMsg[1]);

                updateRoomResidents(GameMaster.getRoomMarkerId(model, endRoomId),
                                    getResidents(endRoomId));

                if (actor != null)
                {
                    int proposedRoomId = actor.getProposedRoomId();

                    Room room = GameMaster.getRoom(model,
                                                   proposedRoomId >
                                                   1 ? proposedRoomId : actor.getRoom());

                    if (proposedRoomId > -1)
                    {
                        GameMaster.moveActor(model, readFrom, proposedRoomId);
                    }

                    Toast.makeText(getApplicationContext(),
                                   actor.getDisplayName() + " opened a door",
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeToClient(
                        NetworkConstants.PREFIX_FEEDBACK + "You opened a door", readFrom);
                    for (int id : GameMaster.getPlayerActorIds(model))
                    {
                        if (id != playerId && id != readFrom)
                        {
                            serverService.writeToClient(
                                NetworkConstants.PREFIX_FEEDBACK + actor.getDisplayName() +
                                " opened a door", id);
                        }
                    }

                    String desc = null;

                    if (room != null)
                    {
                        showAction(room.getMarker(),
                                   readFrom,
                                   -1,
                                   1000,
                                   "door",
                                   null,
                                   true,
                                   false,
                                   desc);
                    }
                }
            }
        }
        else if (msg.startsWith(NetworkConstants.PREFIX_UPDATE_NEAREST_ROOM))
        {
            String[] splitMsg = msg.split(":");
            newNearestRoomMarker(Integer.parseInt(splitMsg[1]), readFrom);
            updateClientTargets();
        }
        else if (msg.startsWith(NetworkConstants.PREFIX_ACTION_REQUEST))
        {
            String[] splitMsg = msg.split(";");

            String[] splitCmd    = splitMsg[1].split("_");
            String[] splitAction = splitCmd[1].split(":");

            switch (splitAction[0])
            {
                case "attack":
                    int targetId = Integer.parseInt(splitAction[1]);
                    attack(readFrom, targetId);
                    break;
                case "defend":
                    GameMaster.setActorState(model, readFrom, Actor.E_STATE.DEFEND);
                    Room room = GameMaster.getActorRoom(model, readFrom);

                    Actor actor = GameMaster.getActor(model, readFrom);

                    String desc = (actor != null) ? actor.getName() : null;

                    if (room != null)
                    {
                        showAction(room.getMarker(),
                                   readFrom,
                                   -1,
                                   1000,
                                   "defend",
                                   null,
                                   true,
                                   false,
                                   desc);
                    }

                    Actor clientActor = GameMaster.getActor(model, readFrom);
                    if (clientActor != null)
                    {
                        Toast.makeText(getApplicationContext(),
                                       clientActor.getDisplayName() + " defended",
                                       Toast.LENGTH_SHORT).show();
                        serverService.writeToClient(
                            NetworkConstants.PREFIX_FEEDBACK + "You defended", readFrom);
                        for (int id : GameMaster.getPlayerActorIds(model))
                        {
                            if (id != playerId && id != readFrom)
                            {
                                serverService.writeToClient(
                                    clientActor.getDisplayName() + " defended", id);
                            }
                        }

                        serverService.writeToClient(NetworkConstants.PREFIX_TURN_OVER +
                                                    clientActor.getHealthMaximum() +
                                                    ":" +
                                                    clientActor.getHealthCurrent() +
                                                    ":" +
                                                    clientActor.getSpecialMaximum() +
                                                    ":" +
                                                    clientActor.getSpecialCurrent(),
                                                    readFrom);
                    }
                    else
                    {
                        serverService.writeToClient(
                            NetworkConstants.PREFIX_TURN_OVER + 1 + ":" + 0 + ":" + 1 + ":" + 0,
                            readFrom);
                    }
                    break;
                case "special":
                    int specialId = Integer.parseInt(splitAction[1]);

                    Special.E_TARGETING_TYPE specialType =
                        GameMaster.getSpecialTargetingType(model, specialId);

                    if (splitAction.length < 3)
                    {
                        if (specialType == Special.E_TARGETING_TYPE.AOE_PLAYER || specialType ==
                                                                                  Special.E_TARGETING_TYPE.AOE_NON_PLAYER)
                        {
                            performSpecial(readFrom, specialId);
                        }
                    }
                    else
                    {
                        performSpecial(readFrom, Integer.parseInt(splitAction[2]), specialId);
                    }
                    break;
                default:
                    break;
            }
        }
        else if (msg.equals(NetworkConstants.GAME_INVENTORY_REQUEST))
        {
            String res = NetworkConstants.PREFIX_INVENTORY_LIST;

            Actor actor = GameMaster.getActor(model, readFrom);

            if (actor != null)
            {
                for (int itemId : actor.getItems().keySet())
                {
                    Item item = GameMaster.getItem(model, itemId);

                    if (item != null)
                    {
                        res += item.getDisplayName() + ",";
                        res += ((item instanceof ItemConsumable) ? "consumable" : "durable") + ",";
                        res += itemId + ":";
                    }
                }
            }

            serverService.writeToClient(res, readFrom);
        }
        else if (msg.equals(NetworkConstants.GAME_FLOOR_REQUEST))
        {
            String res = NetworkConstants.PREFIX_FLOOR_LIST;

            Actor actor = GameMaster.getActor(model, readFrom);
            Room  room  = null;

            if (actor != null && actor.getProposedRoomId() != -1)
            {
                GameMaster.getRoom(model, actor.getProposedRoomId());
            }
            else
            {
                room = GameMaster.getActorRoom(model, playerId);
            }

            if (actor != null && room != null)
            {
                for (int itemId : room.getResidentItems())
                {
                    Item item = GameMaster.getItem(model, itemId);

                    if (item != null)
                    {
                        res += item.getDisplayName() + ",";
                        res += itemId + ":";
                    }
                }
            }

            serverService.writeToClient(res, readFrom);
        }
        else if (msg.startsWith(NetworkConstants.PREFIX_USE_ITEM))
        {
            String[] splitMsg    = msg.split(";");
            String[] splitAction = splitMsg[1].split(":");

            Actor actor = GameMaster.getActor(model, readFrom);
            Item  item  = GameMaster.getItem(model, Integer.parseInt(splitAction[0]));

            String type = null;

            if (actor != null && item != null && item instanceof ItemConsumable)
            {
                switch (((ItemConsumable) item).getTargetingType())
                {
                    case SINGLE_PLAYER:
                        type = ".help";
                        if (splitAction.length < 3)
                        {
                        }
                        else
                        {
                            int   targetId    = Integer.parseInt(splitAction[1]);
                            Actor targetActor = GameMaster.getActor(model, targetId);

                            if (targetActor != null)
                            {
                                if (targetActor.useItem(item))
                                {
                                    actor.removeItem(Integer.parseInt(splitAction[0]));
                                    GameMaster.removeItem(model,
                                                          Integer.parseInt(splitAction[0]));

                                    Toast.makeText(getApplicationContext(),
                                                   actor.getDisplayName() + " used " +
                                                   item.getDisplayName() +
                                                   " on " +
                                                   ((targetId ==
                                                     readFrom) ? "himself" : targetActor.getDisplayName()),
                                                   Toast.LENGTH_SHORT).show();
                                    serverService.writeToClient(
                                        NetworkConstants.PREFIX_FEEDBACK + "You used " +
                                        item.getDisplayName() + " on " + ((targetId ==
                                                                           readFrom) ? "yourself" : targetActor
                                                                              .getDisplayName()),
                                        readFrom);
                                    for (int id : GameMaster.getPlayerActorIds(model))
                                    {
                                        if (id != playerId && id != readFrom)
                                        {
                                            serverService.writeToClient(
                                                actor.getDisplayName() + " used " +
                                                item.getDisplayName() +
                                                " on " +
                                                ((targetId ==
                                                  readFrom) ? "himself" : targetActor.getDisplayName()),
                                                id);
                                        }
                                    }
                                }
                            }
                        }
                        break;
                    case SINGLE_NON_PLAYER:
                        type = ".harm";
                        if (splitAction.length < 3)
                        {
                        }
                        else
                        {
                            int   targetId    = Integer.parseInt(splitAction[1]);
                            Actor targetActor = GameMaster.getActor(model, targetId);

                            if (targetActor != null)
                            {
                                if (targetActor.useItem(item))
                                {
                                    actor.removeItem(Integer.parseInt(splitAction[0]));
                                    GameMaster.removeItem(model,
                                                          Integer.parseInt(splitAction[0]));

                                    Toast.makeText(getApplicationContext(),
                                                   actor.getDisplayName() + " used " +
                                                   item.getDisplayName() +
                                                   " on " +
                                                   ((targetId ==
                                                     readFrom) ? "himself" : actor.getDisplayName()),
                                                   Toast.LENGTH_SHORT).show();
                                    serverService.writeToClient(
                                        NetworkConstants.PREFIX_FEEDBACK + "You used " +
                                        item.getDisplayName() + " on " + ((targetId ==
                                                                           readFrom) ? "yourself" : actor
                                                                              .getDisplayName()),
                                        readFrom);
                                    for (int id : GameMaster.getPlayerActorIds(model))
                                    {
                                        if (id != playerId && id != readFrom)
                                        {
                                            serverService.writeToClient(
                                                actor.getDisplayName() + " used " +
                                                item.getDisplayName() +
                                                " on " +
                                                ((targetId ==
                                                  readFrom) ? "himself" : actor.getDisplayName()),
                                                id);
                                        }
                                    }
                                }
                            }
                        }
                        break;
                    case AOE_PLAYER:
                        type = ".help";
                        for (Actor target : GameMaster.getPlayerTargets(model, readFrom)
                                                      .values())
                        {
                            target.useItem(item);

                            Toast.makeText(getApplicationContext(),
                                           actor.getDisplayName() + " used " +
                                           item.getDisplayName(),
                                           Toast.LENGTH_SHORT).show();
                            serverService.writeToClient(
                                NetworkConstants.PREFIX_FEEDBACK + "You used " +
                                item.getDisplayName(),
                                readFrom);
                            for (int id : GameMaster.getPlayerActorIds(model))
                            {
                                if (id != playerId && id != readFrom)
                                {
                                    serverService.writeToClient(
                                        actor.getDisplayName() + " used " +
                                        item.getDisplayName(),
                                        id);
                                }
                            }
                        }
                        break;
                    case AOE_NON_PLAYER:
                        type = ".harm";
                        for (Actor target : GameMaster.getNonPlayerTargets(model, readFrom)
                                                      .values())
                        {
                            target.useItem(item);

                            Toast.makeText(getApplicationContext(),
                                           actor.getDisplayName() + " used " +
                                           item.getDisplayName(),
                                           Toast.LENGTH_SHORT).show();
                            serverService.writeToClient(
                                NetworkConstants.PREFIX_FEEDBACK + "You used " +
                                item.getDisplayName(),
                                readFrom);
                            for (int id : GameMaster.getPlayerActorIds(model))
                            {
                                if (id != playerId && id != readFrom)
                                {
                                    serverService.writeToClient(
                                        actor.getDisplayName() + " used " +
                                        item.getDisplayName(),
                                        id);
                                }
                            }
                        }
                        break;
                }
            }
            else
            {
                return;
            }

            String playerItems = "";

            for (Item playerItem : actor.getItems().values())
            {
                playerItems +=
                    "," + playerItem.getId() + "." + playerItem.getDisplayName() + "." +
                    ((playerItem instanceof ItemConsumable) ? ((ItemConsumable) playerItem).getTargetingType()
                                                                                           .name() : "DUR");
            }

            if (!playerItems.equals(""))
            {
                playerItems = playerItems.substring(1);
                serverService.writeToClient(
                    NetworkConstants.PREFIX_UPDATE_PLAYER_ITEMS + playerItems, playerId);
            }

            GameMaster.setActorState(model, readFrom, Actor.E_STATE.NEUTRAL);

            Room room = GameMaster.getActorRoom(model, readFrom);

            String desc = item.getName();

            if (room != null)
            {
                showAction(room.getMarker(),
                           readFrom,
                           -1,
                           1000,
                           "item" + type,
                           null,
                           true,
                           false,
                           desc);
            }

            Toast.makeText(getApplicationContext(),
                           actor.getDisplayName() + " used " + item.getDisplayName(),
                           Toast.LENGTH_SHORT).show();
            serverService.writeToClient(
                NetworkConstants.PREFIX_FEEDBACK + "You used " + item.getDisplayName(),
                readFrom);
            for (int id : GameMaster.getPlayerActorIds(model))
            {
                if (id != playerId && id != readFrom)
                {
                    serverService.writeToClient(
                        actor.getDisplayName() + " used " + item.getDisplayName(), id);
                }
            }

            serverService.writeToClient(NetworkConstants.PREFIX_TURN_OVER +
                                        actor.getHealthMaximum() +
                                        ":" +
                                        actor.getHealthCurrent() +
                                        ":" +
                                        actor.getSpecialMaximum() +
                                        ":" +
                                        actor.getSpecialCurrent(),
                                        readFrom);
        }
        else if (msg.startsWith(NetworkConstants.PREFIX_DROP_ITEM))
        {
            String[] splitMsg = msg.split(":");

            Actor actor = GameMaster.getActor(model, readFrom);
            Room  room  = GameMaster.getActorRoom(model, readFrom);
            Item  item  = GameMaster.getItem(model, Integer.parseInt(splitMsg[1]));

            if (actor != null && room != null)
            {
                if (room.getResidentItems().size() < 3)
                {
                    room.addItem(Integer.parseInt(splitMsg[1]));

                    String playerItems = "";

                    for (Item playerItem : actor.getItems().values())
                    {
                        playerItems +=
                            "," + playerItem.getId() + "." + playerItem.getDisplayName() + "." +
                            ((playerItem instanceof ItemConsumable) ? ((ItemConsumable) playerItem).getTargetingType()
                                                                                                   .name() : "DUR");
                    }

                    if (!playerItems.equals(""))
                    {
                        playerItems = playerItems.substring(1);
                        serverService.writeToClient(
                            NetworkConstants.PREFIX_UPDATE_PLAYER_ITEMS + playerItems, playerId);
                    }
                }
                else
                {
                    serverService.writeToClient(
                        NetworkConstants.PREFIX_FEEDBACK + "There's no room to drop an item here!",
                        readFrom);
                }
            }

            if (actor != null && item != null)
            {
                actor.removeItem(item.getId());
            }

            Toast.makeText(this,
                           ((actor != null) ? actor.getDisplayName() : "Someone") + " dropped " +
                           ((item != null) ? item.getDisplayName() : "an item"),
                           Toast.LENGTH_SHORT).show();
            serverService.writeToClient(NetworkConstants.PREFIX_FEEDBACK + "You dropped item " +
                                        ((item != null) ? item.getDisplayName() : "an item"),
                                        readFrom);
            for (int id : GameMaster.getPlayerActorIds(model))
            {
                if (id != playerId && id != readFrom)
                {
                    serverService.writeToClient(NetworkConstants.PREFIX_FEEDBACK + ((actor !=
                                                                                     null) ? actor.getDisplayName() : "Someone") +
                                                " dropped " +
                                                ((item !=
                                                  null) ? item.getDisplayName() : "an item"), id);
                }
            }

            if (actor != null && actor.getItems().size() > 0)
            {
                handleRead(NetworkConstants.GAME_INVENTORY_REQUEST, readFrom);
            }
            else if (actor != null)
            {
                serverService.writeToClient(NetworkConstants.PREFIX_TURN +
                                            actor.getHealthMaximum() +
                                            ":" +
                                            actor.getHealthCurrent() +
                                            ":" +
                                            actor.getSpecialMaximum() +
                                            ":" +
                                            actor.getSpecialCurrent(),
                                            readFrom);
            }
        }
        else if (msg.startsWith(NetworkConstants.PREFIX_TAKE_ITEM))
        {
            String[] splitMsg = msg.split(":");

            Actor actor = GameMaster.getActor(model, readFrom);
            Room  room  = GameMaster.getActorRoom(model, readFrom);
            Item  item  = GameMaster.getItem(model, Integer.parseInt(splitMsg[1]));

            if (actor != null && actor.getItems().size() >= 3)
            {
                serverService.writeToClient(
                    NetworkConstants.PREFIX_FEEDBACK + "Couldn't take item, bag is full", readFrom);

                return;
            }

            if (actor != null && item != null)
            {
                actor.addItem(item);

                serverService.writeToClient(
                    NetworkConstants.PREFIX_FEEDBACK + "Took item " + item.getDisplayName(),
                    readFrom);

                if (PortalActivity.playerId != playerId)
                {
                    String playerItems = "";

                    for (Item playerItem : actor.getItems().values())
                    {
                        playerItems +=
                            "," + playerItem.getId() + "." + playerItem.getDisplayName() + "." +
                            ((playerItem instanceof ItemConsumable) ? ((ItemConsumable) playerItem).getTargetingType()
                                                                                                   .name() : "DUR");
                    }

                    if (!playerItems.equals(""))
                    {
                        playerItems = playerItems.substring(1);
                        serverService.writeToClient(
                            NetworkConstants.PREFIX_UPDATE_PLAYER_ITEMS + playerItems, playerId);
                    }
                }
            }

            if (room != null)
            {
                room.removeItem(Integer.parseInt(splitMsg[1]));
            }

            if (room != null && room.getResidentItems().size() > 0)
            {
                handleRead(NetworkConstants.GAME_FLOOR_REQUEST, readFrom);
            }
            else if (actor != null)
            {
                serverService.writeToClient(NetworkConstants.PREFIX_TURN +
                                            actor.getHealthMaximum() +
                                            ":" +
                                            actor.getHealthCurrent() +
                                            ":" +
                                            actor.getSpecialMaximum() +
                                            ":" +
                                            actor.getSpecialCurrent(),
                                            readFrom);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param msg      Status update
     * @param readFrom Device that update was received from
     */
    @Override
    public void handleStatus(final String msg, int readFrom)
    {
        /*Toast.makeText(this, "Status: " + msg + " from <" + readFrom + ">", Toast.LENGTH_SHORT)
             .show();*/
    }

    /**
     * {@inheritDoc}
     *
     * @param msg      Error message
     * @param readFrom Device that error was received from
     */
    @Override
    public void handleError(final String msg, int readFrom)
    {
        /*Toast.makeText(this, "Error: " + msg + " from <" + readFrom + ">", Toast.LENGTH_SHORT)
             .show();*/
    }

    @Override
    public void newMarker(final int marker)
    {
        if (GameMaster.getMarkerAttachment(model, marker) == -1)
        {
            generateRoom(marker);
        }
    }

    /**
     * Simulate a player {@link Actor actor} moving to a new {@link Room}
     *
     * @param marker   New {@link Room} the {@link Actor} is moving too.
     * @param updateId Used to update a {@link PortalClientActivity client}, use -1 to update self.
     */
    @Override
    public void newNearestRoomMarker(final int marker, int updateId)
    {
        if (GameMaster.getMarkerAttachment(model, marker) == 1)
        {
            int   actorId = updateId == -1 ? playerId : updateId;
            Actor actor   = GameMaster.getActor(model, actorId);
            if (actor != null)
            {
                int lastProposedRoomId = actor.getProposedRoomId();

                if (lastProposedRoomId > -1)
                {
                    if (lastProposedRoomId != actor.getRoom())
                    {
                        GameMaster.removeActorFromRoom(model, actorId, lastProposedRoomId);
                    }
                    updateRoomResidents(GameMaster.getRoomMarkerId(model, lastProposedRoomId),
                                        getResidents(lastProposedRoomId));
                }
            }

            int simRes = GameMaster.simulateMove(model, actorId, marker);

            if (simRes >= 0)
            {
                int curRoomId = GameMaster.getActorRoomId(model, actorId);
                int newRoomId = GameMaster.getRoomIdByMarkerId(model, marker);

                updateRoomResidents(GameMaster.getRoomMarkerId(model, curRoomId),
                                    getResidents(curRoomId));
                updateRoomResidents(marker, getResidents(newRoomId));

                Room room = GameMaster.getRoom(model, newRoomId);

                if (room != null && actor != null && newRoomId != actor.getRoom() &&
                    GameMaster.getPlayersInRoom(model, newRoomId) == 1)
                {
                    int markerID = room.getMarker();
                    short roomSide = GameMaster.getSideOfRoomFrom(model,
                                                                  actor.getRoom(),
                                                                  newRoomId);
                    renderer.updateRoomAlignment(markerID,
                                                 roomSide);
                    serverService.writeAll(
                        NetworkConstants.PREFIX_UPDATE_ROOM_ALIGNMENT + markerID + ":" +
                        roomSide);
                }

                if (updateId == -1)
                {
                    renderer.setPlayerRoomMarker(marker);
                }
                else
                {
                    serverService.writeToClient(NetworkConstants.PREFIX_ASSIGN_ROOM_MARK + marker,
                                                updateId);
                }
            }
        }
    }

    /**
     * Get the reference ID of the first AR marker found by the {@link
     * PortalRenderer} which is not already reserved by a game object, or
     * {@code -1} if no unreserved markers are in view.
     *
     * @return int: The reference ID of the first AR marker found by the {@link
     * PortalRenderer} which is not already reserved by a game object, or
     * {@code -1} if no unreserved markers are in view.
     *
     * @see PortalRenderer
     * @see PortalRenderer#getFirstMarkerExcluding(ArrayList)
     * @see Actor
     * @see Actor#getMarker()
     * @see Room
     * @see Room#getMarker()
     */
    private int getFirstUnreservedMarker()
    {
        ArrayList<Integer> marksX = new ArrayList<>();
        for (Actor actor : model.getActors().values())
        {
            marksX.add(actor.getMarker());
        }
        for (Room room : model.getRooms().values())
        {
            marksX.add(room.getMarker());
        }

        int foundMarker = renderer.getFirstMarkerExcluding(marksX);

        if (foundMarker > -1)
        {
            return foundMarker;
        }

        return -1;
    }

    /**
     * Get the reference ID of the nearest visible AR marker to a given AR
     * marker via the {@link PortalRenderer} which is not associated with an
     * {@link Actor} and is not in the list of AR marker reference IDs
     * indicated as excluded, or {@code -1} if either the given marker is not
     * visible or there are no other markers in view.
     *
     * @param mark0  int: The reference ID of the desired AR marker to get the
     *               nearest marker to.
     * @param marksX ArrayList: A list of marker reference IDs to exclude when
     *               searching for the nearest marker to a given marker.
     *
     * @return int: the reference ID of the nearest visible AR marker to a
     * given AR marker via the {@link PortalRenderer} which is not associated
     * with an {@link Actor} and is not in the list of AR marker reference IDs
     * indicated as excluded, or {@code -1} if either the given marker is not
     * visible or there are no other markers in view.
     *
     * @see PortalRenderer
     * @see PortalRenderer#getNearestMarkerExcluding(int, ArrayList)
     * @see Actor
     * @see Actor#getMarker()
     */
    private int getNearestNonPlayerMarkerExcluding(final int mark0, final ArrayList<Integer> marksX)
    {
        for (Actor actor : model.getActors().values())
        {
            marksX.add(actor.getMarker());
        }

        int foundMarker = renderer.getNearestMarkerExcluding(mark0, marksX);

        if (foundMarker > -1)
        {
            return foundMarker;
        }

        return -1;
    }

    /**
     * Create an {@link Actor} with a given logical reference ID, using a given
     * actor reference name, anchored to a given AR marker. If successful, add
     * the {@link Actor} to the {@link Model} and update the {@link
     * PortalRenderer} appropriately.
     *
     * @param playerId      int: The desired logical reference ID to be used
     *                      for the created {@link Actor}.
     * @param characterName String: The reference name to be used in creating
     *                      a new {@link Actor}; will be used to pull in {@link
     *                      Actor} stats, etc.
     * @param mark          int: The reference ID of the AR marker to anchor
     *                      the new {@link Actor} to.
     *
     * @return boolean:
     * <ul>
     * <li>{@code true}: The given marker reference ID is valid and the {@link
     * Actor} is created.</li>
     * <li>{@code false}: The given marker reference ID is not valid and the
     * {@link Actor} is not created.</li>
     * </ul>
     *
     * @see Actor
     * @see Actor#Actor(int, String, int)
     * @see GameStatLoader
     * @see GameStatLoader#loadActorStats(Actor, String, ConcurrentHashMap, Context)
     * @see Model
     * @see Model#addActor(int, Actor)
     * @see PortalRenderer
     * @see PortalRenderer#setPlayerMarker(int, int)
     */
    private boolean selectPlayerMarker(int playerId, String characterName, int mark)
    {
        if (mark > -1 && GameMaster.getMarkerAttachment(model, mark) == -1)
        {
            Actor actor = new Actor(playerId, characterName, mark);
            GameStatLoader.loadActorStats(actor,
                                          characterName,
                                          model.getSpecials(),
                                          getApplicationContext());
            model.getActors().put(playerId, actor);

            switch (characterName)
            {
                case NetworkConstants.GAME_KNIGHT:
                    renderer.setPlayerMarker(0, mark);
                    break;
                case NetworkConstants.GAME_SOLDIER:
                    renderer.setPlayerMarker(1, mark);
                    break;
                case NetworkConstants.GAME_RANGER:
                    renderer.setPlayerMarker(2, mark);
                    break;
                case NetworkConstants.GAME_WIZARD:
                    renderer.setPlayerMarker(3, mark);
                    break;
            }

            if (PortalActivity.playerId != playerId)
            {
                String playerSpecials = "";

                for (Special special : actor.getSpecials().values())
                {
                    playerSpecials += "," + special.getId() + "." + special.getDisplayName() + "." +
                                      special.getTargetingType().name();
                }

                if (!playerSpecials.equals(""))
                {
                    playerSpecials = playerSpecials.substring(1);
                    serverService.writeToClient(
                        NetworkConstants.PREFIX_UPDATE_PLAYER_SPECIALS + playerSpecials, playerId);
                }
            }

            return true;
        }
        else
        {
            Toast.makeText(getApplicationContext(),
                           "Failed to Find Marker",
                           Toast.LENGTH_SHORT)
                 .show();

            return false;
        }
    }

    /**
     * Create a {@link Room} to use as the starting {@link Room} for all player
     * {@link Actor Actors} and place all player{@link Actor Actors} in that
     * {@link Room}. Starting {@link Room} will have unlocked doors on all
     * sides and no non-player {@link Actor Actors} or {@link Entity Entities}.
     * The starting {@link Room} will be added to the {@link Model}, considered
     * 'placed' and the {@link PortalRenderer} will be updated.
     *
     * @return boolean:
     * <ul>
     * <li>{@code true}: The given marker reference ID is valid and the
     * starting{@link Room} is created.</li>
     * <li>{@code false}: The given marker reference ID is not valid and the
     * starting {@link Room} is not created.</li>
     * </ul>
     *
     * @see Room
     * @see Room#Room(int, int, boolean)
     * @see Actor
     * @see Entity
     * @see Model
     * @see PortalRenderer
     * @see PortalRenderer#createRoom(int, String[])
     * @see PortalRenderer#updateRoomResidents(int, ConcurrentHashMap)
     */
    private boolean selectStartMarker()
    {
        int mark = getFirstUnreservedMarker();
        if (mark > -1)
        {
            //generate a new, placed room
            int  roomId = getNextID(model.getRooms());
            Room room   = new Room(roomId, mark, true);
            model.getRooms().put(roomId, room);

            String[] wallDescriptors = getWallDescriptors(roomId);

            createRoom(mark, wallDescriptors);
            serverService.writeAll(NetworkConstants.PREFIX_PLACE_ROOM + mark);
            serverService.writeAll(NetworkConstants.PREFIX_ASSIGN_ROOM_MARK + mark);

            //place every player actor in that room
            for (Actor actor : model.getActors().values())
            {
                if (actor.isPlayer())
                {
                    actor.setRoom(roomId);
                    room.addActor(actor.getId());
                }
            }

            ConcurrentHashMap<Integer, Pair<Boolean, String>> residents = getResidents(roomId);
            updateRoomResidents(room.getMarker(), residents);

            model.getMap().init(roomId);

            Toast.makeText(getApplicationContext(),
                           "Starting Room Established",
                           Toast.LENGTH_SHORT)
                 .show();

            renderer.setPlayerRoomMarker(mark);
            renderer.setCheckingNearestRoomMarker(true);

            return true;
        }
        else
        {
            Toast.makeText(getApplicationContext(),
                           "Failed to Find Valid Marker",
                           Toast.LENGTH_SHORT)
                 .show();

            return false;
        }
    }

    private boolean generateRoom(int mark)
    {
        if (mark > -1 && GameMaster.getMarkerAttachment(model, mark) == -1)
        {
            Room room =
                GameMaster.generateRoom(this,
                                        model,
                                        CollectionManager.getNextID(model.getRooms()),
                                        mark);

            if (room != null)
            {
                String[] wallDescriptors = getWallDescriptors(room.getId());

                createRoom(mark, wallDescriptors);

                /*runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Toast.makeText(getApplicationContext(),
                                       "New Room Generated",
                                       Toast.LENGTH_SHORT)
                             .show();
                    }
                });*/
            }

            return true;
        }
        else
        {
            Toast.makeText(getApplicationContext(),
                           "Couldn't Find Valid Marker",
                           Toast.LENGTH_SHORT)
                 .show();

            return false;
        }
    }

    private static short getWallFromAngle(final float angle)
    {
        if (angle > 315 || angle <= 45)
        {
            return Room.WALL_TOP;
        }
        else if (angle > 45 && angle <= 135)
        {
            return Room.WALL_RIGHT;
        }
        else if (angle > 135 && angle <= 225)
        {
            return Room.WALL_BOTTOM;
        }
        else
        {
            return Room.WALL_LEFT;
        }
    }

    private boolean openDoor()
    {
        Actor actor = GameMaster.getActor(model, playerId);

        int startRoomId = (actor != null &&
                           actor.getProposedRoomId() != -1) ? actor.getProposedRoomId() : GameMaster
                              .getActorRoomId(model, playerId);

        ArrayList<Integer> placedRoomMarkers = GameMaster.getPlacedRoomMarkerIds(model);

        int nearestMarkerID =
            getNearestNonPlayerMarkerExcluding(GameMaster.getRoomMarkerId(model, startRoomId),
                                               placedRoomMarkers);
        if (nearestMarkerID > -1)
        {
            if (GameMaster.getMarkerAttachment(model, nearestMarkerID) == 1)
            {
                int endRoomId = GameMaster.getIdByMarker(model, nearestMarkerID);

                float angle0 =
                    renderer.getAngleBetweenMarkers(GameMaster.getRoomMarkerId(model, startRoomId),
                                                    nearestMarkerID);
                float angle1 = renderer.getAngleBetweenMarkers(nearestMarkerID,
                                                               GameMaster.getRoomMarkerId(model,
                                                                                          startRoomId));

                short sideOfStartRoom = getWallFromAngle(angle0);
                short sideOfEndRoom   = getWallFromAngle(angle1);

                int res =
                    GameMaster.openDoor(model,
                                        startRoomId,
                                        endRoomId,
                                        sideOfStartRoom,
                                        sideOfEndRoom);

                postOpenDoorResult(endRoomId, res, playerId);

                if (res >= 0)
                {
                    serverService.writeAll(NetworkConstants.PREFIX_PLACE_ROOM + nearestMarkerID);

                    updateRoomResidents(GameMaster.getRoomMarkerId(model, endRoomId),
                                        getResidents(endRoomId));

                    return true;
                }

                return false;
            }
            else
            {
                Toast.makeText(getApplicationContext(),
                               "Error: Tried to Open Door to a Room Which does not Exist\nPlease Generate the Room First",
                               Toast.LENGTH_SHORT)
                     .show();

                return false;
            }
        }
        else
        {
            Toast.makeText(getApplicationContext(),
                           "Couldn't Find Valid Marker",
                           Toast.LENGTH_SHORT)
                 .show();

            return false;
        }
    }

    private void postOpenDoorResult(final int endRoomId, final int res, final int sourceActorId)
    {
        if (sourceActorId == playerId)
        {
            switch (res)
            {
                case 0:
                    break;
                default:
                    Toast.makeText(this,
                                   "Couldn't open door, bad adjacency of new room",
                                   Toast.LENGTH_SHORT).show();
                    break;
            }
        }
        else
        {
            switch (res)
            {
                case 0:
                    break;
                default:
                    serverService.writeToClient(NetworkConstants.PREFIX_FEEDBACK +
                                                "Couldn't open door, bad adjacency of new room",
                                                sourceActorId);
                    break;
            }
        }

        if (res >= 0)
        {
            Room room = GameMaster.getRoom(model, endRoomId);
            if (room != null)
            {
                updateRoomWalls(room.getMarker(), getWallDescriptors(endRoomId));
            }

            ArrayList<Integer> adjacentRoomIds = GameMaster.getAdjacentRoomIds(model, endRoomId);
            for (int id : adjacentRoomIds)
            {
                Room adjacentRoom = GameMaster.getRoom(model, id);
                if (adjacentRoom != null)
                {
                    updateRoomWalls(adjacentRoom.getMarker(), getWallDescriptors(id));
                }
            }
        }
    }

    private void attack(final int attackerId, final int defenderId)
    {
        int res = GameMaster.attack(model, attackerId, defenderId);

        postAttackResults(attackerId, defenderId, res);
    }

    private void postAttackResults(final int attackerId, final int defenderId, final int res)
    {
        Actor attacker = GameMaster.getActor(model, attackerId);
        Actor defender = GameMaster.getActor(model, defenderId);

        if (attackerId == playerId)
        {
            switch (res)
            {
                case 5:
                    Toast.makeText(this,
                                   ((defender != null) ? defender.getDisplayName() : "Someone") +
                                   " dropped something!",
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeAll(
                        NetworkConstants.PREFIX_FEEDBACK +
                        ((defender != null) ? defender.getDisplayName() : "Someone") +
                        " dropped something!");
                    break;
                case 4:
                    //Toast.makeText(this, "All players defeated!", Toast.LENGTH_SHORT).show();
                    loseCondition = true;
                    break;
                case 3:
                    //Toast.makeText(this, "Player downed!", Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    //Toast.makeText(this, "Boss defeated!", Toast.LENGTH_SHORT).show();
                    winCondition = true;
                    break;
                case 1:
                    Toast.makeText(this,
                                   "You killed " +
                                   ((defender != null) ? defender.getDisplayName() : "Someone"),
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeAll(
                        NetworkConstants.PREFIX_FEEDBACK +
                        ((attacker != null) ? attacker.getDisplayName() : "Someone") + " killed " +
                        ((defender != null) ? defender.getDisplayName() : "Someone"));
                    break;
                case 0:
                    Toast.makeText(this,
                                   "You attacked " +
                                   ((defender != null) ? defender.getDisplayName() : "Someone"),
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeAll(
                        NetworkConstants.PREFIX_FEEDBACK +
                        ((attacker != null) ? attacker.getDisplayName() : "Someone") +
                        " attacked " +
                        ((defender != null) ? defender.getDisplayName() : "Someone"));
                    break;
                default:
                    break;
            }
        }
        else
        {
            switch (res)
            {
                case 5:
                    Toast.makeText(this,
                                   ((defender != null) ? defender.getDisplayName() : "Someone") +
                                   " dropped something!",
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeAll(
                        NetworkConstants.PREFIX_FEEDBACK +
                        ((defender != null) ? defender.getDisplayName() : "Someone") +
                        " dropped something!");
                    break;
                case 4:
                    loseCondition = true;
                    break;
                case 3:
                    break;
                case 2:
                    winCondition = true;
                    break;
                case 1:
                    Toast.makeText(this,
                                   ((attacker != null) ? attacker.getDisplayName() : "Someone") +
                                   " killed " +
                                   ((defender != null) ? defender.getDisplayName() : "Someone"),
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeToClient(NetworkConstants.PREFIX_FEEDBACK + "You killed " +
                                                ((defender !=
                                                  null) ? defender.getDisplayName() : "Someone"),
                                                attackerId);
                    for (int id : GameMaster.getPlayerActorIds(model))
                    {
                        if (id != playerId && id != attackerId)
                        {
                            serverService.writeToClient(
                                NetworkConstants.PREFIX_FEEDBACK +
                                ((attacker != null) ? attacker.getDisplayName() : "Someone") +
                                " killed " +
                                ((defender != null) ? defender.getDisplayName() : "Someone"), id);
                        }
                    }
                    break;
                case 0:
                    Toast.makeText(this,
                                   ((attacker != null) ? attacker.getDisplayName() : "Someone") +
                                   " attacked " +
                                   ((defender != null) ? defender.getDisplayName() : "Someone"),
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeToClient(NetworkConstants.PREFIX_FEEDBACK + "You attacked " +
                                                ((defender !=
                                                  null) ? defender.getDisplayName() : "Someone"),
                                                attackerId);
                    for (int id : GameMaster.getPlayerActorIds(model))
                    {
                        if (id != playerId && id != attackerId)
                        {
                            serverService.writeToClient(
                                NetworkConstants.PREFIX_FEEDBACK +
                                ((attacker != null) ? attacker.getDisplayName() : "Someone") +
                                " attacked " +
                                ((defender != null) ? defender.getDisplayName() : "Someone"), id);
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        if (res >= 0)
        {
            renderer.setCheckingNearestRoomMarker(false);

            Room room = GameMaster.getActorRoom(model, attackerId);
            //Actor defender = GameMaster.getActor(model, defenderId);

            String desc = (attacker != null) ? attacker.getName() : null;

            if (room != null)
            {
                int markerID = room.getMarker();
                String targetState = (defender != null) ? (defender.getState() ==
                                                           Actor.E_STATE.DEFEND ? "defend" : null) : null;
                showAction(markerID,
                           attackerId,
                           defenderId,
                           1000,
                           "attack",
                           targetState,
                           true,
                           true,
                           desc);
            }

            if (attackerId == playerId)
            {
                Actor actor = GameMaster.getActor(model, playerId);

                if (actor != null)
                {
                    uiPortalOverlay.overlayWaitingForTurn(actor.getHealthMaximum(),
                                                          actor.getHealthCurrent(),
                                                          actor.getSpecialMaximum(),
                                                          actor.getSpecialCurrent());
                }
                else
                {
                    uiPortalOverlay.overlayWaitingForTurn(1, 0, 1, 0);
                }
            }
            else if (GameMaster.getPlayerActorIds(model).contains(attackerId))
            {
                Actor clientActor = GameMaster.getActor(model, attackerId);
                if (clientActor != null)
                {
                    serverService.writeToClient(NetworkConstants.PREFIX_TURN_OVER +
                                                clientActor.getHealthMaximum() +
                                                ":" +
                                                clientActor.getHealthCurrent() +
                                                ":" +
                                                clientActor.getSpecialMaximum() +
                                                ":" +
                                                clientActor.getSpecialCurrent(),
                                                attackerId);
                }
                else
                {
                    serverService.writeToClient(
                        NetworkConstants.PREFIX_TURN_OVER + 1 + ":" + 0 + ":" + 1 + ":" + 0,
                        attackerId);
                }
            }

            if (defenderId == playerId)
            {
                Actor actor = GameMaster.getActor(model, playerId);

                if (actor != null)
                {
                    uiPortalOverlay.overlayWaitingForTurn(actor.getHealthMaximum(),
                                                          actor.getHealthCurrent(),
                                                          actor.getSpecialMaximum(),
                                                          actor.getSpecialCurrent());
                }
                else
                {
                    uiPortalOverlay.overlayWaitingForTurn(1, 0, 1, 0);
                }
            }
            else if (GameMaster.getPlayerActorIds(model).contains(defenderId))
            {
                Actor clientActor = GameMaster.getActor(model, defenderId);
                if (clientActor != null)
                {
                    serverService.writeToClient(NetworkConstants.PREFIX_TURN_OVER +
                                                clientActor.getHealthMaximum() +
                                                ":" +
                                                clientActor.getHealthCurrent() +
                                                ":" +
                                                clientActor.getSpecialMaximum() +
                                                ":" +
                                                clientActor.getSpecialCurrent(),
                                                defenderId);
                }
                else
                {
                    serverService.writeToClient(
                        NetworkConstants.PREFIX_TURN_OVER + 1 + ":" + 0 + ":" + 1 + ":" + 0,
                        defenderId);
                }
            }
        }
    }

    /**
     * Attempt to perform a {@link Special} ability originating from a given
     * {@link Actor} without a specific target using the appropriate {@link
     * GameMaster} method, then post the results using the result code provided
     * by the {@link GameMaster} method.
     *
     * @param sourceId  int: The logical reference ID of the desired {@link
     *                  Actor} from which {@link Special} ability originated.
     * @param specialId int: The logical reference ID of the desired {@link
     *                  Special} ability to attempt to perform.
     *
     * @see Special
     * @see Actor
     * @see GameMaster
     * @see GameMaster#special(Model, int, int)
     */
    private void performSpecial(final int sourceId, final int specialId)
    {
        int res = GameMaster.special(model, sourceId, specialId);

        postSpecialResult(sourceId, -1, specialId, res);
    }

    /**
     * Attempt to perform a {@link Special} ability originating from a given
     * {@link Actor} and targeted at a given {@link Actor} using the
     * appropriate {@link GameMaster} method, then post the results using the
     * result code provided by the {@link GameMaster} method.
     *
     * @param sourceId  int: The logical reference ID of the desired {@link
     *                  Actor} from which the {@link Special} ability
     *                  originated.
     * @param targetId  int: The logical reference ID of the desired {@link
     *                  Actor} at which the {@link Special} ability is
     *                  targeted.
     * @param specialId int: The logical reference ID of the desired {@link
     *                  Special} ability to attempt to perform.
     *
     * @see Special
     * @see Actor
     * @see GameMaster
     * @see GameMaster#special(Model, int, int, int)
     */
    private void performSpecial(final int sourceId, final int targetId, final int specialId)
    {
        int res = GameMaster.special(model, sourceId, targetId, specialId);

        postSpecialResult(sourceId, targetId, specialId, res);
    }

    /**
     * Post the results of attempting to perform a {@link Special} ability in a
     * way which provides feedback to the user, e.g. a Toast with a message
     * describing why the {@link Special} could not be performed, or an
     * 'animation' in the {@link PortalRenderer}.
     *
     * @param sourceId  int: The logical reference ID of the desired {@link
     *                  Actor} from which the {@link Special} ability
     *                  originated.
     * @param targetId  int: The logical reference ID of the desired {@link
     *                  Actor} at which the {@link Special} ability is
     *                  targeted, or {@code -1} if the {@link Special ability}
     *                  does not have a specific target.
     * @param specialId int: The logical reference ID of the desired {@link
     *                  Special} ability which was attempted to be performed.
     * @param res       int: A result code provided by the {@link GameMaster}
     *                  when the {@link Special} ability is attempted.
     *
     * @see Special
     * @see Actor
     * @see GameMaster
     * @see GameMaster#special(Model, int, int)
     * @see GameMaster#special(Model, int, int, int)
     * @see PortalRenderer
     * @see PortalRenderer#showAction(int, int, int, long, String, String, boolean, boolean, String)
     */
    private void postSpecialResult(final int sourceId,
                                   final int targetId,
                                   final int specialId,
                                   final int res)
    {
        Actor   source  = GameMaster.getActor(model, sourceId);
        Actor   target  = (targetId > -1) ? GameMaster.getActor(model, targetId) : null;
        Special special = GameMaster.getSpecial(model, specialId);

        if (sourceId == playerId)
        {
            switch (res)
            {
                case 6:
                    Toast.makeText(this,
                                   "One of them dropped something!",
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeAll(
                        NetworkConstants.PREFIX_FEEDBACK + "One of them dropped something!");
                    break;
                case 5:
                    Toast.makeText(this,
                                   ((target != null) ? target.getDisplayName() : "Someone") +
                                   " dropped something!",
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeAll(
                        NetworkConstants.PREFIX_FEEDBACK +
                        ((target != null) ? target.getDisplayName() : "Someone") +
                        " dropped something!");
                    break;
                case 4:
                    //Toast.makeText(this, "All players defeated!", Toast.LENGTH_SHORT).show();
                    loseCondition = true;
                    break;
                case 3:
                    //Toast.makeText(this, "Player downed!", Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    //Toast.makeText(this, "Boss defeated!", Toast.LENGTH_SHORT).show();
                    winCondition = true;
                    break;
                case 1:
                    Toast.makeText(this,
                                   "You killed " +
                                   ((target != null) ? target.getDisplayName() : "Someone"),
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeAll(
                        NetworkConstants.PREFIX_FEEDBACK +
                        ((source != null) ? source.getDisplayName() : "Someone") + " killed " +
                        ((target != null) ? target.getDisplayName() : "Someone") + " with " +
                        ((special != null) ? special.getDisplayName() : "Something"));
                    break;
                case 0:
                    Toast.makeText(this,
                                   "You used " +
                                   ((special != null) ? special.getDisplayName() : "Something") +
                                   ((targetId > -1) ?
                                    " on " +
                                    ((targetId == sourceId) ? "yourself" : ((target !=
                                                                             null) ? target
                                                                                .getDisplayName() : "Someone")) : ""),
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeAll(
                        NetworkConstants.PREFIX_FEEDBACK +
                        ((source != null) ? source.getDisplayName() : "Someone") + " used " +
                        ((special != null) ? special.getDisplayName() : "Something") +
                        ((targetId > -1) ?
                         " on " +
                         ((targetId == sourceId) ? "yourself" : ((target !=
                                                                  null) ? target
                                                                     .getDisplayName() : "Someone")) : ""));
                    break;
                case -1:
                    Toast.makeText(this, "You don't have enough energy", Toast.LENGTH_SHORT).show();
                    break;
                case -2:
                    Toast.makeText(this, "No valid targets", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
        else
        {
            switch (res)
            {
                case 6:
                    Toast.makeText(this,
                                   "One of them dropped something!",
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeAll(
                        NetworkConstants.PREFIX_FEEDBACK + "One of them dropped something!");
                    break;
                case 5:
                    Toast.makeText(this,
                                   ((target != null) ? target.getDisplayName() : "Someone") +
                                   " dropped something!",
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeAll(
                        NetworkConstants.PREFIX_FEEDBACK +
                        ((target != null) ? target.getDisplayName() : "Someone") +
                        " dropped something!");
                    break;
                case 4:
                    loseCondition = true;
                    break;
                case 3:
                    break;
                case 2:
                    winCondition = true;
                    break;
                case 1:
                    Toast.makeText(this,
                                   ((source != null) ? source.getDisplayName() : "Someone") +
                                   " killed " +
                                   ((target != null) ? target.getDisplayName() : "Someone") +
                                   " with " +
                                   ((special != null) ? special.getDisplayName() : "Something"),
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeToClient(
                        NetworkConstants.PREFIX_FEEDBACK + "You killed " +
                        ((target != null) ? target.getDisplayName() : "Someone"),
                        sourceId);
                    for (int id : GameMaster.getPlayerActorIds(model))
                    {
                        if (id != playerId && id != sourceId)
                        {
                            serverService.writeToClient(
                                NetworkConstants.PREFIX_FEEDBACK +
                                ((source != null) ? source.getDisplayName() : "Someone") +
                                " killed " +
                                ((target != null) ? target.getDisplayName() : "Someone") +
                                " with " +
                                ((special != null) ? special.getDisplayName() : "Something"), id);
                        }
                    }
                    break;
                case 0:
                    Toast.makeText(this,
                                   ((source != null) ? source.getDisplayName() : "Someone") +
                                   " used " +
                                   ((special != null) ? special.getDisplayName() : "Something") +
                                   ((targetId > -1) ?
                                    " on " +
                                    ((targetId == sourceId) ? "yourself" : ((target !=
                                                                             null) ? target
                                                                                .getDisplayName() : "Someone")) : ""),
                                   Toast.LENGTH_SHORT).show();
                    serverService.writeToClient(
                        NetworkConstants.PREFIX_FEEDBACK + "You used " +
                        ((special != null) ? special.getDisplayName() : "Something") +
                        ((targetId > -1) ?
                         " on " +
                         ((targetId == sourceId) ? "yourself" : ((target !=
                                                                  null) ? target
                                                                     .getDisplayName() : "Someone")) : ""),
                        sourceId);
                    for (int id : GameMaster.getPlayerActorIds(model))
                    {
                        if (id != playerId && id != sourceId)
                        {
                            serverService.writeToClient(
                                NetworkConstants.PREFIX_FEEDBACK +
                                ((source != null) ? source.getDisplayName() : "Someone") +
                                " used " +
                                ((special != null) ? special.getDisplayName() : "Something") +
                                ((targetId > -1) ?
                                 " on " +
                                 ((targetId == sourceId) ? "yourself" : ((target !=
                                                                          null) ? target
                                                                             .getDisplayName() : "Someone")) : ""),
                                id);
                        }
                    }
                    break;
                case -1:
                    serverService.writeToClient(
                        NetworkConstants.PREFIX_FEEDBACK + "You don't have enough energy",
                        sourceId);
                    break;
                case -2:
                    serverService.writeToClient(
                        NetworkConstants.PREFIX_FEEDBACK + "No valid targets", sourceId);
                    break;
                default:
                    break;
            }
        }

        if (res >= 0)
        {
            renderer.setCheckingNearestRoomMarker(false);

            Room room = GameMaster.getActorRoom(model, sourceId);
            //Actor target = GameMaster.getActor(model, targetId);

            String specialType = GameMaster.getSpecialTypeDescriptor(model, specialId);

            String desc = (special != null) ? special.getName() : null;

            if (room != null)
            {
                showAction(room.getMarker(),
                           sourceId,
                           targetId,
                           1000,
                           "special." + specialType,
                           ((target != null) ? (target.getState() ==
                                                Actor.E_STATE.DEFEND ? "defend" : null) : null),
                           true,
                           specialType.equals("harm"),
                           desc);
            }

            if (sourceId == playerId)
            {
                Actor actor = GameMaster.getActor(model, playerId);

                if (actor != null)
                {
                    uiPortalOverlay.overlayWaitingForTurn(actor.getHealthMaximum(),
                                                          actor.getHealthCurrent(),
                                                          actor.getSpecialMaximum(),
                                                          actor.getSpecialCurrent());
                }
                else
                {
                    uiPortalOverlay.overlayWaitingForTurn(1, 0, 1, 0);
                }
            }
            else if (GameMaster.getPlayerActorIds(model).contains(sourceId))
            {
                Actor clientActor = GameMaster.getActor(model, sourceId);
                if (clientActor != null)
                {
                    serverService.writeToClient(NetworkConstants.PREFIX_TURN_OVER +
                                                clientActor.getHealthMaximum() +
                                                ":" +
                                                clientActor.getHealthCurrent() +
                                                ":" +
                                                clientActor.getSpecialMaximum() +
                                                ":" +
                                                clientActor.getSpecialCurrent(),
                                                sourceId);
                }
                else
                {
                    serverService.writeToClient(
                        NetworkConstants.PREFIX_TURN_OVER + 1 + ":" + 0 + ":" + 1 + ":" + 0,
                        sourceId);
                }
            }

            if (targetId == playerId)
            {
                Actor actor = GameMaster.getActor(model, playerId);

                if (actor != null)
                {
                    uiPortalOverlay.overlayWaitingForTurn(actor.getHealthMaximum(),
                                                          actor.getHealthCurrent(),
                                                          actor.getSpecialMaximum(),
                                                          actor.getSpecialCurrent());
                }
                else
                {
                    uiPortalOverlay.overlayWaitingForTurn(1, 0, 1, 0);
                }
            }
            else if (GameMaster.getPlayerActorIds(model).contains(targetId))
            {
                Actor clientActor = GameMaster.getActor(model, targetId);
                if (clientActor != null)
                {
                    serverService.writeToClient(NetworkConstants.PREFIX_TURN_OVER +
                                                clientActor.getHealthMaximum() +
                                                ":" +
                                                clientActor.getHealthCurrent() +
                                                ":" +
                                                clientActor.getSpecialMaximum() +
                                                ":" +
                                                clientActor.getSpecialCurrent(),
                                                targetId);
                }
                else
                {
                    serverService.writeToClient(
                        NetworkConstants.PREFIX_TURN_OVER + 1 + ":" + 0 + ":" + 1 + ":" + 0,
                        targetId);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param message String: The feedback message to be processed or given to
     */
    @Override
    public void feedback(final String message)
    {
        Runnable uiUpdate = new Runnable()
        {
            @Override
            public void run()
            {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        };

        runOnUiThread(uiUpdate);

        serverService.writeAll(NetworkConstants.PREFIX_FEEDBACK + message);
    }

    /**
     * {@inheritDoc}
     *
     * @param sourceId int: The logical reference ID of the {@link Actor}
     *                 performing the action
     * @param targetId int: The logical reference ID of the {@link Actor}
     *                 target of the action if applicable, -1 otherwise.
     * @param action   String: Reference description of the action being
     *                 performed.
     */
    @Override
    public void onActorAction(final int sourceId,
                              final int targetId,
                              final String action,
                              @Nullable final String desc)
    {
        Logger.logD("enter trace");

        Logger.logD("received action for actor " + sourceId);

        boolean forwardAction = (action.equals("attack") || action.equals("special.harm"));

        Room  room   = GameMaster.getActorRoom(model, sourceId);
        Actor target = GameMaster.getActor(model, targetId);

        Logger.logD("showing action in renderer");

        if (room != null)
        {
            showAction(room.getMarker(),
                       sourceId,
                       targetId,
                       1000,
                       action,
                       ((target != null) ? (target.getState() ==
                                            Actor.E_STATE.DEFEND ? "defend" : null) : null),
                       false,
                       forwardAction,
                       desc);
        }

        if (targetId == playerId)
        {
            Actor actor = GameMaster.getActor(model, playerId);

            if (actor != null)
            {
                uiPortalOverlay.setHealth(actor.getHealthCurrent());

                if (actor.getHealthCurrent() <= 0)
                {
                    Toast.makeText(this, "Player downed!", Toast.LENGTH_SHORT).show();
                    serverService.writeAll(NetworkConstants.PREFIX_FEEDBACK + "Player downed!");
                }
            }
            else
            {
                uiPortalOverlay.setHealth(0);
            }
        }
        else if (GameMaster.getPlayerActorIds(model).contains(targetId))
        {
            Actor clientActor = GameMaster.getActor(model, targetId);
            if (clientActor != null)
            {
                serverService.writeToClient(
                    NetworkConstants.PREFIX_HEALTH + clientActor.getHealthCurrent(), targetId);

                if (clientActor.getHealthCurrent() <= 0)
                {
                    Toast.makeText(this, "Player downed!", Toast.LENGTH_SHORT).show();
                    serverService.writeAll(NetworkConstants.PREFIX_FEEDBACK + "Player downed!");
                }
            }
            else
            {
                serverService.writeToClient(NetworkConstants.PREFIX_TURN_OVER + 0, targetId);
            }
        }

        Logger.logD("exit trace");
    }

    /**
     * {@inheritDoc}
     *
     * @param actorId int: The logical reference ID of the {@link Actor} which
     *                has moved.
     * @param roomId  int: The logical reference ID of the {@link Room} moved
     *                to by the given {@link Actor}.
     */
    @Override
    public void onActorMove(final int actorId, final int roomId)
    {
        Logger.logD("enter trace");

        Logger.logD("received move for actor " + actorId + " to room " + roomId);

        int startRoomId = GameMaster.getActorRoomId(model, actorId);

        GameMaster.moveActor(model, actorId, roomId);

        Room room = GameMaster.getRoom(model, startRoomId);
        if (room != null)
        {
            updateRoomResidents(room.getMarker(), getResidents(room.getId()));
        }
        room = GameMaster.getRoom(model, roomId);
        if (room != null)
        {
            updateRoomResidents(room.getMarker(), getResidents(room.getId()));
        }

        updateClientTargets();

        if (GameMaster.getPlayersInRoom(model, roomId) > 0)
        {
            Runnable turnDelayer = new Runnable()
            {
                @Override
                public void run()
                {
                    postTurn(actorId);
                }
            };
            handler.postDelayed(turnDelayer, 1000);
            Logger.logD("postTurn posted to execute in " + 1000);
        }
        else
        {
            postTurn(actorId);
        }

        Logger.logD("exit trace");
    }

    /**
     * {@inheritDoc}
     *
     * @param turnId int: The logical reference ID of the turn that was passed,
     *               and also the {@link Actor} for which that turn was taken.
     */
    @Override
    public void turnPassed(final int turnId)
    {
        Logger.logD("enter trace");

        Logger.logD("ended non-player turn " + turnId);

        postTurn(turnId);

        Logger.logD("exit trace");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFinishedLoading()
    {
        Runnable uiUpdate = new Runnable()
        {
            @Override
            public void run()
            {
                uiPortalOverlay.overlayPlayerMarkerSelect();
            }
        };

        runOnUiThread(uiUpdate);
    }

    /**
     * {@inheritDoc}
     *
     * @param actorId int: The logical reference ID of the {@link Actor}
     *                performing the action which has finished.
     */
    @Override
    public void onFinishedAction(final int actorId)
    {
        Logger.logD("enter trace");

        Logger.logD("finished action for " + actorId);

        Logger.logD("removing dead actors");
        GameMaster.removeDeadActors(model);
        Logger.logD("updating renderer room");

        Room room = GameMaster.getActorRoom(model, actorId);
        if (room != null)
        {
            updateRoomResidents(room.getMarker(), getResidents(room.getId()));
        }

        updateClientTargets();

        if (turn)
        {
            turn = false;
        }

        Runnable turnDelayer = new Runnable()
        {
            @Override
            public void run()
            {
                postTurn(actorId);
            }
        };
        handler.postDelayed(turnDelayer, 1000);
        Logger.logD("postTurn posted to execute in " + 1000);

        Logger.logD("exit trace");
    }

    /**
     * Update room residents for the client and host
     *
     * @param arRoomId  MarkerID of the room to update
     * @param residents Updated list of residents
     *
     * @see PortalRenderer#updateRoomResidents(int, ConcurrentHashMap)
     */
    private void updateRoomResidents(int arRoomId,
                                     ConcurrentHashMap<Integer, Pair<Boolean, String>> residents)
    {
        renderer.updateRoomResidents(arRoomId, residents);
        String clientRoomResidents = "";
        // Use a different delimiter, because of how residents are stored
        for (Integer residentID : residents.keySet())
        {
            Pair<Boolean, String> pair = residents.get(residentID);
            clientRoomResidents = clientRoomResidents.concat(
                residentID + ";" + pair.first + "," + pair.second + "~");
        }
        serverService.writeAll(
            NetworkConstants.PREFIX_UPDATE_ROOM_RESIDENTS + arRoomId + "~" + clientRoomResidents);
    }

    /**
     * Show actions for client and host
     *
     * @param arRoomId     int: The reference ID of the AR marker the desired
     *                     {@link ARRoom} in which the action is taking place
     *                     is anchored to. Should match the marker reference ID
     *                     of exactly one {@link Room}.
     * @param playerId     int: The logical reference ID of the source {@link
     *                     Actor} of the action being shown.
     * @param targetId     int: The logical reference ID of the target {@link
     *                     Actor} at which the action being shown is directed,
     *                     or {@code -1} if the action does not have a specific
     *                     target.
     * @param length       int: The duration in milliseconds to present the
     *                     desired action.
     * @param actionType   String: A description of the action being shown,
     *                     e.g. {@code attack}, {@code defend},
     *                     {@code special:hurt}.
     *                     <p>
     *                     Note: {@link Special} actions must include a
     *                     description of the special type ({@code hurt} or
     *                     {@code help}, see {@link
     *                     GameMaster#getSpecialTypeDescriptor(Model, int)})
     *                     delimited by a {@code :}.
     * @param targetState  String: A description of the state of the target
     *                     {@link Actor} of the desired action, or {@code null}
     *                     if the action being shown does not have a specific
     *                     target {@link Actor}.
     * @param playerAction boolean: A flag indicating whether the source {@link
     *                     Actor} of the action being shown is a player
     *                     controlled {@link Actor}.
     *                     <ul>
     *                     <li>true: The source {@link Actor} is considered to
     *                     be player controlled.</li>
     *                     <li>false: The source {@link Actor} is not
     *                     considered to be player controlled.</li>
     *                     </ul>
     * @param forward      boolean: A flag indicating whether the action
     *                     requires its source and target (if applicable){@link
     *                     Actor Actors} in the 'forward' position - closer to
     *                     room center, directly opposite one another.
     *                     <ul>
     *                     <li>true: The action requires its source and target
     *                     (if applicable) {@link Actor Actors} in the
     *                     'forward' position.</li>
     *                     <li>false: The action DOES NOT require its source
     *                     and target (if applicable) {@link Actor Actors} in
     *                     the 'forward' position.</li>
     *                     </ul>
     *
     * @see PortalRenderer#showAction(int, int, int, long, String, String, boolean, boolean, String)
     */
    private void showAction(int arRoomId,
                            int playerId,
                            int targetId,
                            int length,
                            String actionType,
                            @Nullable String targetState,
                            boolean playerAction,
                            boolean forward,
                            @Nullable String desc)
    {
        renderer.showAction(arRoomId,
                            playerId,
                            targetId,
                            length,
                            actionType,
                            targetState,
                            playerAction,
                            forward,
                            desc);
        serverService.writeAll(
            NetworkConstants.PREFIX_SHOW_ACTION + arRoomId + ":" + playerId + ":" + targetId + ":" +
            length + ":" + actionType + ":" + targetState + ":" + playerAction + ":" + forward +
            ":" + desc);
    }

    /**
     * Create room for client and host
     *
     * @param roomID          MarkerID of new room
     * @param wallDescriptors Array of wall descriptors
     *
     * @see PortalRenderer#createRoom(int, String[])
     */
    private void createRoom(int roomID, String[] wallDescriptors)
    {
        renderer.createRoom(roomID, wallDescriptors);
        String clientWallDescriptors = "";
        for (String str : wallDescriptors)
        {
            clientWallDescriptors = clientWallDescriptors.concat(str + ":");
        }
        serverService.writeAll(
            NetworkConstants.PREFIX_CREATE_ROOM + roomID + ":" + clientWallDescriptors);
        serverService.writeAll(NetworkConstants.PREFIX_RESERVE_ROOM_MARKER + roomID);
    }

    /**
     * Update room walls for client and host
     *
     * @param arRoomID        MarkerID of room to update
     * @param wallDescriptors Updated array of wall descriptors
     *
     * @see PortalRenderer#updateRoomWalls(int, String[])
     */
    private void updateRoomWalls(int arRoomID, String[] wallDescriptors)
    {
        renderer.updateRoomWalls(arRoomID, wallDescriptors);
        String clientWallDescriptors = "";
        for (String str : wallDescriptors)
        {
            clientWallDescriptors = clientWallDescriptors.concat(str + ":");
        }
        serverService.writeAll(
            NetworkConstants.PREFIX_UPDATE_ROOM_WALLS + arRoomID + ":" + clientWallDescriptors);
    }

    /**
     * Called when a turn has ended.
     *
     * @param actorId int: The logical reference ID of the {@link Actor} which
     *                just took a turn.
     */
    private void postTurn(final int actorId)
    {
        Logger.logD("enter trace");

        loseCondition = GameMaster.areAllPlayersDead(model);

        if (winCondition)
        {
            Toast.makeText(this, "Boss defeated!", Toast.LENGTH_SHORT).show();
            serverService.writeAll(NetworkConstants.PREFIX_FEEDBACK + "Boss defeated!");
            uiPortalOverlay.overlayWinCondition();
            serverService.writeAll(NetworkConstants.GAME_WIN_CONDITION);
        }
        else if (loseCondition)
        {
            Toast.makeText(this, "All players defeated!", Toast.LENGTH_SHORT).show();
            serverService.writeAll(NetworkConstants.PREFIX_FEEDBACK + "All players defeated!");
            uiPortalOverlay.overlayLoseCondition();
            serverService.writeAll(NetworkConstants.GAME_LOSE_CONDITION);
        }
        else
        {
            Actor actor = GameMaster.getActor(model, actorId);

            if (actor != null)
            {
                actor.tick();

                if (actorId == playerId)
                {
                    uiPortalOverlay.overlayWaitingForTurn(actor.getHealthMaximum(),
                                                          actor.getHealthCurrent(),
                                                          actor.getSpecialMaximum(),
                                                          actor.getSpecialCurrent());
                }
                else if (GameMaster.getPlayerActorIds(model).contains(actorId))
                {
                    serverService.writeToClient(NetworkConstants.PREFIX_TURN_OVER +
                                                actor.getHealthMaximum() +
                                                ":" +
                                                actor.getHealthCurrent() +
                                                ":" +
                                                actor.getSpecialMaximum() +
                                                ":" +
                                                actor.getSpecialCurrent(),
                                                actorId);
                }
            }

            turnId = CollectionManager.getNextIdFromId(turnId, model.getActors());

            Logger.logD("next turn is " + turnId);

            Actor nextActor = GameMaster.getActor(model, turnId);

            /*if (nextActor != null)
            {
                nextActor.tick();
            }*/

            if (nextActor != null && nextActor.getHealthCurrent() <= 0)
            {
                Logger.logD("actor <" + turnId + "> is incapacitated");

                postTurn(turnId);
            }
            else
            {
                if (turnId == playerId)
                {
                    turn = true;
                    Actor hostActor = GameMaster.getActor(model, playerId);

                    if (hostActor != null)
                    {
                        uiPortalOverlay.overlayAction(hostActor.getHealthMaximum(),
                                                      hostActor.getHealthCurrent(),
                                                      hostActor.getSpecialMaximum(),
                                                      hostActor.getSpecialCurrent());
                    }
                    else
                    {
                        uiPortalOverlay.overlayAction(1, 0, 1, 0);
                    }

                    renderer.setCheckingNearestRoomMarker(true);
                }
                else if (!GameMaster.getActorIsPlayer(model, turnId))
                {
                    Logger.logD(
                        "turn id is not a player, taking turn for non-player actor " + turnId);

                    ActorController.takeTurn(this, model, turnId);
                }
                else
                {
                    Actor clientActor = GameMaster.getActor(model, turnId);
                    if (clientActor != null)
                    {
                        serverService.writeToClient(NetworkConstants.PREFIX_TURN +
                                                    clientActor.getHealthMaximum() +
                                                    ":" +
                                                    clientActor.getHealthCurrent() +
                                                    ":" +
                                                    clientActor.getSpecialMaximum() +
                                                    ":" +
                                                    clientActor.getSpecialCurrent(),
                                                    turnId);
                    }
                    else
                    {
                        serverService.writeToClient(
                            NetworkConstants.PREFIX_TURN + 1 + ":" + 0 + ":" + 1 + ":" + 0, turnId);
                    }
                }
            }
        }

        Logger.logD("exit trace");
    }

    /**
     * Construct a list of String wall descriptors describing the walls of a
     * {@link Room} in a way that is meaningful to the {@link PortalRenderer}.
     *
     * @param roomId int: The logical reference ID of the desired {@link Room}.
     *
     * @return String[]: An array of {@link Room} wall descriptors meaningful
     * to the {@link PortalRenderer}.
     *
     * @see Room
     * @see PortalRenderer
     * @see PortalRenderer#createRoom(int, String[])
     * @see PortalRenderer#updateRoomWalls(int, String[])
     */
    @NonNull
    private String[] getWallDescriptors(final int roomId)
    {
        Room room = GameMaster.getRoom(model, roomId);

        String[] wallDescriptors = new String[4];

        if (room != null)
        {
            for (short i = 0; i < wallDescriptors.length; ++i)
            {
                switch (room.getWallType(i))
                {
                    case NO_DOOR:
                        wallDescriptors[i] = "room_wall";
                        break;
                    case DOOR_UNLOCKED:
                        wallDescriptors[i] = "room_door_unlocked";
                        break;
                    case DOOR_OPEN:
                        wallDescriptors[i] = "room_door_open";
                        break;
                    case DOOR_LOCKED:
                        wallDescriptors[i] = "room_door_locked";
                        break;
                }
            }
        }

        return wallDescriptors;
    }

    /**
     * Construct a map of {@link Actor} logical reference IDs to Pairs of a
     * boolean value representing whether or not that {@link Actor} is player
     * controlled and a String providing the reference name of that {@link
     * Actor} and the state of that {@link Actor} as a pose name meaningful to
     * the {@link PortalRenderer} separated by a colon (<code>:</code>).
     *
     * @param roomId int: The logical reference ID of the {@link Room} to to
     *               get the list of resident {@link Actor Actors} from.
     *
     * @return ConcurrentHashMap: A map indexing Pairs of a boolean value
     * representing whether or not an {@link Actor} is a player and a String
     * providing that {@link Actor Actor's} reference name and state separated
     * by a colon (<code>:</code>) by {@link Actor} logical reference ID.
     *
     * @see Actor
     * @see Room
     * @see PortalRenderer
     * @see PortalRenderer#updateRoomResidents(int, ConcurrentHashMap)
     */
    @NonNull
    private ConcurrentHashMap<Integer, Pair<Boolean, String>> getResidents(final int roomId)
    {
        ConcurrentHashMap<Integer, Pair<Boolean, String>> res = new ConcurrentHashMap<>();

        Room room = GameMaster.getRoom(model, roomId);

        if (room != null)
        {
            for (int id : room.getResidentActors())
            {
                Actor actor = GameMaster.getActor(model, id);

                if (actor != null)
                {
                    String name = actor.getName();

                    String pose = "default";

                    if (actor.getHealthCurrent() <= 0)
                    {
                        pose = "wounded";
                    }
                    else
                    {
                        switch (actor.getState())
                        {
                            case NEUTRAL:
                            case ATTACK:
                            case SPECIAL:
                                if (GameMaster.getActorIsPlayer(model, id))
                                {
                                    if (GameMaster.getEnemiesInRoom(model, room.getId()) > 0)
                                    {
                                        pose = "ready";
                                    }
                                    else
                                    {
                                        pose = "idle";
                                    }
                                }
                                else
                                {
                                    if (GameMaster.getPlayersInRoom(model, room.getId()) > 0)
                                    {
                                        pose = "ready";
                                    }
                                    else
                                    {
                                        pose = "idle";
                                    }

                                }
                                break;
                            case DEFEND:
                                pose = "defend";
                                break;
                        }
                    }

                    Pair<Boolean, String> residentDescriptor =
                        new Pair<>(GameMaster.getActorIsPlayer(model, id),
                                   name + ":" + pose);

                    res.put(id, residentDescriptor);
                }
            }
        }

        return res;
    }

    private void updateClientTargets()
    {
        ArrayList<Integer> playerActorIds = GameMaster.getPlayerActorIds(model);

        for (int i : playerActorIds)
        {
            if (i != playerId)
            {
                String nonPlayerTargets = "";

                for (Actor actor : GameMaster.getNonPlayerTargets(model, i).values())
                {
                    nonPlayerTargets +=
                        "," + actor.getId() + "." + actor.getDisplayName();
                }

                if (!nonPlayerTargets.equals(""))
                {
                    nonPlayerTargets = nonPlayerTargets.substring(1);
                }

                serverService.writeToClient(
                    NetworkConstants.PREFIX_UPDATE_NON_PLAYER_TARGETS +
                    nonPlayerTargets, i);

                String playerTargets = "";

                for (Actor actor : GameMaster.getPlayerTargets(model, i).values())
                {
                    playerTargets +=
                        "," + actor.getId() + "." + actor.getDisplayName();
                }

                if (!playerTargets.equals(""))
                {
                    playerTargets = playerTargets.substring(1);
                }

                serverService.writeToClient(
                    NetworkConstants.PREFIX_UPDATE_PLAYER_TARGETS +
                    playerTargets, i);
            }
        }
    }

    // Defines callbacks for service binding, passed to bindService()
    private ServiceConnection mServerConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder)
        {
            // We've bound to ServerService, cast the IBinder and get ServerService instance
            ServerService.LocalBinder binder = (ServerService.LocalBinder) iBinder;
            serverService = binder.getService();
            mServerBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            mServerBound = false;
        }
    };

    private void toggleMusic()
    {
        musicEnabled = !musicEnabled;

        if (musicEnabled)
        {
            mediaPlayer = MediaPlayer.create(this, R.raw.overworld);
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
        }
        else
        {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}