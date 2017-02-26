package com.semaphore_soft.apps.cypher;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;

import com.semaphore_soft.apps.cypher.game.Actor;
import com.semaphore_soft.apps.cypher.game.GameController;
import com.semaphore_soft.apps.cypher.game.GameMaster;
import com.semaphore_soft.apps.cypher.game.Room;
import com.semaphore_soft.apps.cypher.opengl.ARDrawableGLES20;
import com.semaphore_soft.apps.cypher.opengl.ARModelGLES20;
import com.semaphore_soft.apps.cypher.opengl.ARRoom;
import com.semaphore_soft.apps.cypher.opengl.ModelLoader;
import com.semaphore_soft.apps.cypher.opengl.shader.DynamicShaderProgram;
import com.semaphore_soft.apps.cypher.opengl.shader.ShaderLoader;
import com.semaphore_soft.apps.cypher.utils.GameStatLoader;
import com.semaphore_soft.apps.cypher.utils.Logger;

import org.artoolkit.ar.base.ARToolKit;
import org.artoolkit.ar.base.rendering.gles20.ARRendererGLES20;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by rickm on 11/9/2016.
 */

class PortalRenderer extends ARRendererGLES20
{
    private Context context;

    private static GameController gameController;

    private static ArrayList<Integer> markers;
    private static int[]              playerMarkerIDs;

    private static ArrayList<ARModelGLES20>                    characterModels;
    private static ConcurrentHashMap<String, ARDrawableGLES20> models;
    private static ConcurrentHashMap<Integer, ARRoom>          arRooms;

    private static Handler handler;

    @Override
    public boolean configureARScene()
    {
        markers = new ArrayList<>();

        markers.add(ARToolKit.getInstance().addMarker("single;Data/00.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/01.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/02.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/03.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/04.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/05.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/06.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/07.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/08.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/09.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/10.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/11.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/12.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/13.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/14.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/15.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/16.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/17.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/18.patt;80"));
        markers.add(ARToolKit.getInstance().addMarker("single;Data/19.patt;80"));

        for (int id : markers)
        {
            if (id < 0)
            {
                return false;
            }
        }

        playerMarkerIDs = new int[]{-1, -1, -1, -1};

        return true;
    }

    //Shader calls should be within a GL thread that is onSurfaceChanged(), onSurfaceCreated() or onDrawFrame()
    //As the cube instantiates the shader during setShaderProgram call we need to create the cube here.
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config)
    {
        super.onSurfaceCreated(unused, config);

        characterModels = new ArrayList<>();

        for (int i = 0; i < 4; ++i)
        {
            ARModelGLES20 playerMarkerModel =
                (ARModelGLES20) ModelLoader.load(context, "waypoint", 40.0f);
            playerMarkerModel.setCharacter(i);
            DynamicShaderProgram characterShaderProgram =
                new DynamicShaderProgram(ShaderLoader.createShader(context,
                                                                   "shaders/vertexShaderTextured.glsl",
                                                                   GLES20.GL_VERTEX_SHADER),
                                         ShaderLoader.createShader(context,
                                                                   "shaders/fragmentShaderTextured.glsl",
                                                                   GLES20.GL_FRAGMENT_SHADER),
                                         new String[]{"a_Position", "a_Color", "a_Normal", "a_TexCoordinate"});
            playerMarkerModel.setShaderProgram(characterShaderProgram);
            characterModels.add(playerMarkerModel);
        }

        arRooms = new ConcurrentHashMap<>();

        models = new ConcurrentHashMap<>();

        ARDrawableGLES20 waypoint =
            ModelLoader.load(context, "waypoint", 40.0f);
        DynamicShaderProgram waypointShaderProgram =
            new DynamicShaderProgram(ShaderLoader.createShader(context,
                                                               "shaders/vertexShaderTextured.glsl",
                                                               GLES20.GL_VERTEX_SHADER),
                                     ShaderLoader.createShader(context,
                                                               "shaders/fragmentShaderTextured.glsl",
                                                               GLES20.GL_FRAGMENT_SHADER),
                                     new String[]{"a_Position", "a_Color", "a_Normal", "a_TexCoordinate"});
        waypoint.setShaderProgram(waypointShaderProgram);
        models.put("waypoint", waypoint);

        ARDrawableGLES20 overlay =
            ModelLoader.load(context, "overlay", 40.0f);
        DynamicShaderProgram overlayShaderProgram =
            new DynamicShaderProgram(ShaderLoader.createShader(context,
                                                               "shaders/vertexShaderTextured.glsl",
                                                               GLES20.GL_VERTEX_SHADER),
                                     ShaderLoader.createShader(context,
                                                               "shaders/fragmentShaderTextured.glsl",
                                                               GLES20.GL_FRAGMENT_SHADER),
                                     new String[]{"a_Position", "a_Color", "a_Normal", "a_TexCoordinate"});
        overlay.setShaderProgram(overlayShaderProgram);
        models.put("overlay", overlay);

        ArrayList<String> actorNames = GameStatLoader.getList(context, "actors");
        if (actorNames != null)
        {
            for (String name : actorNames)
            {
                ARDrawableGLES20 actorModel =
                    ModelLoader.load(context, name, "actors");
                if (actorModel != null)
                {
                    DynamicShaderProgram actorShaderProgram =
                        new DynamicShaderProgram(ShaderLoader.createShader(context,
                                                                           "shaders/vertexShaderUntextured.glsl",
                                                                           GLES20.GL_VERTEX_SHADER),
                                                 ShaderLoader.createShader(context,
                                                                           "shaders/fragmentShaderUntextured.glsl",
                                                                           GLES20.GL_FRAGMENT_SHADER),
                                                 new String[]{"a_Position", "a_Color", "a_Normal"});
                    actorModel.setShaderProgram(actorShaderProgram);
                    models.put(name, actorModel);
                }
            }
        }

        ARDrawableGLES20 roomBase =
            ModelLoader.load(context, "room_base", 120.0f, "room_base");
        DynamicShaderProgram roomBaseShaderProgram =
            new DynamicShaderProgram(ShaderLoader.createShader(context,
                                                               "shaders/vertexShaderTextured.glsl",
                                                               GLES20.GL_VERTEX_SHADER),
                                     ShaderLoader.createShader(context,
                                                               "shaders/fragmentShaderTextured.glsl",
                                                               GLES20.GL_FRAGMENT_SHADER),
                                     new String[]{"a_Position", "a_Color", "a_Normal"});
        roomBase.setShaderProgram(roomBaseShaderProgram);
        //roomBase.setColor(0.5f, 0.5f, 0.5f, 1.0f);
        models.put("room_base", roomBase);

        ARDrawableGLES20 roomWall =
            ModelLoader.load(context, "room_wall_north", 120.0f, "room_base");
        DynamicShaderProgram roomWallShaderProgram =
            new DynamicShaderProgram(ShaderLoader.createShader(context,
                                                               "shaders/vertexShaderTextured.glsl",
                                                               GLES20.GL_VERTEX_SHADER),
                                     ShaderLoader.createShader(context,
                                                               "shaders/fragmentShaderTextured.glsl",
                                                               GLES20.GL_FRAGMENT_SHADER),
                                     new String[]{"a_Position", "a_Color", "a_Normal"});
        roomWall.setShaderProgram(roomWallShaderProgram);
        //roomWall.setColor(0.5f, 0.5f, 0.5f, 1.0f);
        models.put("room_wall", roomWall);

        ARDrawableGLES20 roomDoor =
            ModelLoader.load(context, "room_door_north", 120.0f, "room_base");
        DynamicShaderProgram roomDoorShaderProgram =
            new DynamicShaderProgram(ShaderLoader.createShader(context,
                                                               "shaders/vertexShaderTextured.glsl",
                                                               GLES20.GL_VERTEX_SHADER),
                                     ShaderLoader.createShader(context,
                                                               "shaders/fragmentShaderTextured.glsl",
                                                               GLES20.GL_FRAGMENT_SHADER),
                                     new String[]{"a_Position", "a_Color", "a_Normal"});
        roomDoor.setShaderProgram(roomDoorShaderProgram);
        //roomDoor.setColor(0.5f, 0.25f, 0.125f, 1.0f);
        models.put("room_door_unlocked", roomDoor);

        ARDrawableGLES20 roomDoorOpen =
            ModelLoader.load(context, "room_door_north_open", 120.0f, "room_base");
        DynamicShaderProgram roomDoorOpenShaderProgram =
            new DynamicShaderProgram(ShaderLoader.createShader(context,
                                                               "shaders/vertexShaderTextured.glsl",
                                                               GLES20.GL_VERTEX_SHADER),
                                     ShaderLoader.createShader(context,
                                                               "shaders/fragmentShaderTextured.glsl",
                                                               GLES20.GL_FRAGMENT_SHADER),
                                     new String[]{"a_Position", "a_Color", "a_Normal"});
        roomDoorOpen.setShaderProgram(roomDoorOpenShaderProgram);
        //roomDoorOpen.setColor(0.75f, 0.75f, 0.75f, 1.0f);
        models.put("room_door_open", roomDoorOpen);

        ARDrawableGLES20 error = ModelLoader.load(context, "error", 120.0f);
        DynamicShaderProgram errorShaderProgram =
            new DynamicShaderProgram(ShaderLoader.createShader(context,
                                                               "shaders/vertexShaderUntextured.glsl",
                                                               GLES20.GL_VERTEX_SHADER),
                                     ShaderLoader.createShader(context,
                                                               "shaders/fragmentShaderUntextured.glsl",
                                                               GLES20.GL_FRAGMENT_SHADER),
                                     new String[]{"a_Position", "a_Color", "a_Normal"});
        error.setShaderProgram(errorShaderProgram);
        error.setColor(0.7f, 0.0f, 0.0f, 1.0f);
        models.put("error", error);

        gameController.onFinishedLoading();
    }

    /**
     * Override the render function from {@link ARRendererGLES20}.
     */
    @Override
    public void draw()
    {
        super.draw();

        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glFrontFace(GLES20.GL_CW);

        float[] projectionMatrix = ARToolKit.getInstance().getProjectionMatrix();

        for (int i = 0; i < playerMarkerIDs.length; ++i)
        {
            if (playerMarkerIDs[i] > -1 &&
                ARToolKit.getInstance().queryMarkerVisible(playerMarkerIDs[i]))
            {
                characterModels.get(i).draw(projectionMatrix,
                                            ARToolKit.getInstance()
                                                     .queryMarkerTransformation(playerMarkerIDs[i]));
                models.get("overlay")
                      .draw(projectionMatrix,
                            ARToolKit.getInstance().queryMarkerTransformation(playerMarkerIDs[i]));
            }
        }

        for (int i : arRooms.keySet())
        {
            if (ARToolKit.getInstance().queryMarkerVisible(i))
            {
                arRooms.get(i)
                       .draw(projectionMatrix,
                             ARToolKit.getInstance().queryMarkerTransformation(i));
            }
        }
    }

    public void setContext(final Context context)
    {
        this.context = context;
    }

    static void setHandler(final Handler handler)
    {
        PortalRenderer.handler = handler;
    }

    static void setGameController(final GameController gameController)
    {
        PortalRenderer.gameController = gameController;
    }

    int getFirstMarker()
    {
        for (int id : markers)
        {
            if (ARToolKit.getInstance().queryMarkerVisible(id))
            {
                return id;
            }
        }

        return -1;
    }

    int getFirstMarkerExcluding(final ArrayList<Integer> marksX)
    {
        for (int id : markers)
        {
            if (!marksX.contains(id) && ARToolKit.getInstance().queryMarkerVisible(id))
            {
                return id;
            }
        }

        return -1;
    }

    int getNearestMarker(final int mark0)
    {
        int    nearest          = -1;
        double shortestDistance = -1;

        if (ARToolKit.getInstance().queryMarkerVisible(mark0))
        {
            for (int mark1 : markers)
            {
                if (mark0 != mark1 && ARToolKit.getInstance().queryMarkerVisible(mark1))
                {
                    float[] mark0TransInfo =
                        ARToolKit.getInstance().queryMarkerTransformation(mark0);
                    float[] mark0PosInfo =
                        {mark0TransInfo[mark0TransInfo.length - 4], mark0TransInfo[
                            mark0TransInfo.length -
                            3], mark0TransInfo[
                            mark0TransInfo.length - 2]};

                    float[] mark1TransInfo =
                        ARToolKit.getInstance().queryMarkerTransformation(mark1);
                    float[] mark1PosInfo =
                        {mark1TransInfo[mark1TransInfo.length - 4], mark1TransInfo[
                            mark1TransInfo.length -
                            3], mark1TransInfo[
                            mark1TransInfo.length - 2]};

                    double thisDistance =
                        Math.sqrt(Math.pow(mark0PosInfo[0] - mark1PosInfo[0], 2) +
                                  Math.pow(mark0PosInfo[1] - mark1PosInfo[1], 2) +
                                  Math.pow(mark0PosInfo[2] - mark1PosInfo[2], 2));

                    if (thisDistance < shortestDistance || shortestDistance < 0)
                    {
                        nearest = mark1;
                        shortestDistance = thisDistance;
                    }
                }
            }
        }

        return nearest;
    }

    int getNearestMarkerExcluding(final int mark0, final ArrayList<Integer> marksX)
    {
        int    nearest          = -1;
        double shortestDistance = -1;

        if (ARToolKit.getInstance().queryMarkerVisible(mark0))
        {
            for (int mark1 : markers)
            {
                if (!marksX.contains(mark1) && mark0 != mark1 &&
                    ARToolKit.getInstance().queryMarkerVisible(mark1))
                {
                    float[] mark0TransInfo =
                        ARToolKit.getInstance().queryMarkerTransformation(mark0);
                    float[] mark0PosInfo =
                        {mark0TransInfo[mark0TransInfo.length - 4], mark0TransInfo[
                            mark0TransInfo.length -
                            3], mark0TransInfo[
                            mark0TransInfo.length - 2]};

                    float[] mark1TransInfo =
                        ARToolKit.getInstance().queryMarkerTransformation(mark1);
                    float[] mark1PosInfo =
                        {mark1TransInfo[mark1TransInfo.length - 4], mark1TransInfo[
                            mark1TransInfo.length -
                            3], mark1TransInfo[
                            mark1TransInfo.length - 2]};

                    double thisDistance =
                        Math.sqrt(Math.pow(mark0PosInfo[0] - mark1PosInfo[0], 2) +
                                  Math.pow(mark0PosInfo[1] - mark1PosInfo[1], 2) +
                                  Math.pow(mark0PosInfo[2] - mark1PosInfo[2], 2));

                    if (thisDistance < shortestDistance || shortestDistance < 0)
                    {
                        nearest = mark1;
                        shortestDistance = thisDistance;
                    }
                }
            }
        }

        return nearest;
    }

    float getMarkerDirection(final int mark0)
    {
        float res;

        float[] mark0TransInfo =
            ARToolKit.getInstance().queryMarkerTransformation(mark0);

        for (int i = 0; i < mark0TransInfo.length; i += 4)
        {
            String output = "";
            for (int j = 0; j < 4; ++j)
            {
                output += mark0TransInfo[i + j] + " ";
            }
            System.out.println(output);
        }

        res = ((mark0TransInfo[4] >= 0) ? (float) Math.acos(mark0TransInfo[0]) : (float) (Math.PI +
                                                                                          Math.acos(
                                                                                              -mark0TransInfo[0])));

        res *= (180 / Math.PI);
        System.out.println("Flat angle in degrees: " + res);

        return ((Float.isNaN(res)) ? 0 : res);
    }

    float getAngleBetweenMarkers(final int mark0, final int mark1)
    {
        float[] resVector;

        float[] mark0TransInfo =
            ARToolKit.getInstance().queryMarkerTransformation(mark0);
        float[] mark1TransInfo =
            ARToolKit.getInstance().queryMarkerTransformation(mark1);

        mark1TransInfo[12] -= mark0TransInfo[12];
        mark1TransInfo[13] -= mark0TransInfo[13];
        mark1TransInfo[14] -= mark0TransInfo[14];

        mark0TransInfo[12] -= mark0TransInfo[12];
        mark0TransInfo[13] -= mark0TransInfo[13];
        mark0TransInfo[14] -= mark0TransInfo[14];

        float[] mark0TransInv = new float[16];
        Matrix.invertM(mark0TransInv, 0, mark0TransInfo, 0);

        resVector =
            new float[]{mark1TransInfo[12], mark1TransInfo[13], mark1TransInfo[14], mark1TransInfo[15]};

        String output = "ResVector Before Multiply: ";
        for (float aResVector : resVector)
        {
            output += aResVector + " ";
        }
        System.out.println(output);

        Matrix.multiplyMV(resVector,
                          0,
                          mark0TransInv,
                          0,
                          resVector,
                          0);

        output = "ResVector After Multiply: ";
        for (float aResVector : resVector)
        {
            output += aResVector + " ";
        }

        float resAngle = (float) Math.atan2(resVector[0], resVector[1]);
        resAngle *= (180 / Math.PI);
        resAngle = ((resAngle < 0) ? (360 + resAngle) : resAngle);

        System.out.println(output);

        return ((Float.isNaN(resAngle)) ? 0 : resAngle);
    }

    void setPlayerMarker(final int playerID, final int markerID)
    {
        playerMarkerIDs[playerID] = markerID;
    }

    //TODO set duration
    void setEnemyEffect(Actor actor)
    {
        int roomId = actor.getRoom();
        Logger.logD("roomID: " + roomId);
        arRooms.get(roomId).addEffect(actor.getId(), models.get("overlay"));
    }

    void createRoom(final Room room)
    {
        ARRoom arRoom = new ARRoom();
        arRoom.setRoomModel(models.get("room_base"));
        for (short i = 0; i < 4; ++i)
        {
            switch (room.getWallType(i))
            {
                case NO_DOOR:
                    arRoom.setWall(i, models.get("room_wall"));
                    break;
                case DOOR_UNLOCKED:
                    arRoom.setWall(i, models.get("room_door_unlocked"));
                    break;
                case DOOR_OPEN:
                    arRoom.setWall(i, models.get("room_door_open"));
                    break;
                case DOOR_LOCKED:
                    arRoom.setWall(i, models.get("room_door_locked"));
                    break;
            }
        }
        arRooms.put(room.getMarker(), arRoom);
    }

    void updateRoomWalls(final Room room)
    {
        ARRoom arRoom = arRooms.get(room.getMarker());
        for (short i = 0; i < 4; ++i)
        {
            switch (room.getWallType(i))
            {
                case NO_DOOR:
                    arRoom.setWall(i, models.get("room_wall"));
                    break;
                case DOOR_UNLOCKED:
                    arRoom.setWall(i, models.get("room_door_unlocked"));
                    break;
                case DOOR_OPEN:
                    arRoom.setWall(i, models.get("room_door_open"));
                    break;
                case DOOR_LOCKED:
                    arRoom.setWall(i, models.get("room_door_locked"));
                    break;
            }
        }
    }

    void updateRoomAlignment(final Room room, final short side)
    {
        ARRoom arRoom = arRooms.get(room.getMarker());
        arRoom.setAlignment(side);
    }

    void updateRoomResidents(final Room room, final ConcurrentHashMap<Integer, Actor> actors)
    {
        ARRoom arRoom = arRooms.get(room.getMarker());
        arRoom.removeActors();
        for (int id : room.getResidentActors())
        {
            if (actors.keySet().contains(id))
            {
                String name = actors.get(id).getName();
                System.out.println("adding actor:" + id + ":" + name + " to room:" + room.getId());
                Actor actor = actors.get(id);

                if (name != null && models.keySet().contains(name))
                {
                    if (actor.isPlayer())
                    {
                        arRoom.addPlayer(id, models.get(name));
                    }
                    else
                    {
                        arRoom.addEnemy(id, models.get(name));
                    }
                    switch (actor.getState())
                    {
                        case NEUTRAL:
                            //break;
                        case ATTACK:
                            //arRoom.setResidentPose(id, "attack");
                            //break;
                        case SPECIAL:
                            //arRoom.setResidentPose(id, "special");
                            arRoom.setResidentPose(id, "default");
                            break;
                        case DEFEND:
                            arRoom.setResidentPose(id, "defend");
                            break;
                    }
                }
                else
                {
                    if (actor.isPlayer())
                    {
                        arRoom.addPlayer(id, models.get("error"));
                    }
                    else
                    {
                        arRoom.addEnemy(id, models.get("error"));
                    }
                }
            }
        }
    }

    void showAction(final Room room,
                    final ConcurrentHashMap<Integer, Actor> actors,
                    final int sourceId,
                    final int targetId,
                    final long length,
                    final String actionType,
                    final boolean forward)
    {
        final int roomId = room.getMarker();
        ARRoom    arRoom = arRooms.get(roomId);
        if (forward)
        {
            if (GameMaster.getActorIsPlayer(sourceId))
            {
                arRoom.setForwardPlayer(sourceId);
                if (targetId > -1)
                {
                    arRoom.setForwardEnemy(targetId);
                }
            }
            else
            {
                arRoom.setForwardEnemy(sourceId);
                if (targetId > -1)
                {
                    arRoom.setForwardPlayer(targetId);
                }
            }
        }

        String[] splitAction = actionType.split(":");

        switch (splitAction[0])
        {
            case "attack":
                arRoom.setResidentPose(sourceId, "attack");
                if (targetId > -1)
                {
                    if (!(actors.get(targetId).getState() == Actor.E_STATE.DEFEND))
                    {
                        arRoom.setResidentPose(targetId, "hurt");
                    }
                    else
                    {
                        arRoom.setResidentPose(targetId, "defend");
                    }
                }
                break;
            case "defend":
                arRoom.setResidentPose(sourceId, "defend");
                break;
            case "special":
                arRoom.setResidentPose(sourceId, "special");
                if (targetId != sourceId)
                {
                    switch (splitAction[1])
                    {
                        case "harm":
                            if (!(actors.get(targetId).getState() == Actor.E_STATE.DEFEND))
                            {
                                arRoom.setResidentPose(targetId, "hurt");
                            }
                            else
                            {
                                arRoom.setResidentPose(targetId, "defend");
                            }
                            break;
                        case "help":
                            arRoom.setResidentPose(targetId, "defend");
                            break;
                    }
                }
                break;
        }

        Runnable actionFinisher = new Runnable()
        {
            @Override
            public void run()
            {
                concludeAction(roomId, actors, sourceId, targetId);
            }
        };
        handler.postDelayed(actionFinisher, length);
    }

    private void concludeAction(final int roomId,
                                final ConcurrentHashMap<Integer, Actor> actors,
                                final int sourceId,
                                final int targetId)
    {
        ARRoom arRoom = arRooms.get(roomId);
        arRoom.clearForwardPlayer();
        arRoom.clearForwardEnemy();

        if (actors.get(sourceId).getState() == Actor.E_STATE.DEFEND)
        {
            arRoom.setResidentPose(sourceId, "defend");
        }
        else
        {
            arRoom.setResidentPose(sourceId, "default");
        }

        if (actors.contains(targetId))
        {
            if (actors.get(sourceId).getState() == Actor.E_STATE.DEFEND)
            {
                arRoom.setResidentPose(sourceId, "defend");
            }
            else
            {
                arRoom.setResidentPose(sourceId, "default");
            }
        }

        gameController.onFinishedAction(sourceId);
    }

    interface NewMarkerListener
    {
        void newMarker(int marker);
    }
}
