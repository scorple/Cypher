package com.semaphore_soft.apps.cypher.game;

import com.semaphore_soft.apps.cypher.utils.Logger;
import com.semaphore_soft.apps.cypher.utils.Lottery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ActorController game.ActorController} provides the logic necessary to
 * decide what action a given non-player {@link Actor} should take, and then
 * notify the attached {@link GameController} of that action. Requires a {@link
 * Model} for decision making purposes.
 *
 * @author scorple
 * @see Actor
 * @see GameController
 * @see Model
 */
public class ActorController
{
    /**
     * Given a non-player {@link Actor} ID, decide what action that {@link
     * Actor} will take and take that action via necessary calls to {@link
     * GameMaster}. The {@link GameController} will then be notified of this
     * action for presentation to the user.
     * <p>
     * Action decision will be made based on what actions are available to the
     * {@link Actor}, checked against the state of the {@link Model}, and the
     * {@link Actor Actor's} ticket counts for each available action. The total
     * of all ticket counts will be summed and a random ticket selected in that
     * range. The action selected will depend on which action's ticket range
     * the randomly selected ticket number falls within, working as a
     * 'lottery'.
     *
     * @param actorId int: The logical reference ID of the {@link Actor} for
     *                which an action is being selected an taken.
     *
     * @see Actor
     * @see Model
     * @see GameMaster
     * @see GameController
     */
    public static void takeTurn(final GameController gameController,
                                final Model model,
                                final int actorId)
    {
        Actor actor = GameMaster.getActor(model, actorId);

        Logger.logI("taking turn for actor " + actor.getName());

        int attackTickets  = 0;
        int defendTickets  = 0;
        int specialTickets = 0;
        int useItemTickets = 0;

        int itemId = -1;

        ArrayList<Integer> playerTargets =
            GameMaster.getPlayerTargetIds(model, actorId);
        ArrayList<Integer> nonPlayerTargets =
            GameMaster.getNonPlayerTargetIds(model, actorId);
        ConcurrentHashMap<Integer, Special> actorSpecials = actor.getSpecials();
        if (playerTargets.size() > 0)
        {
            attackTickets = actor.getAttackTickets();
            defendTickets = actor.getDefendTickets();

            if (actorSpecials != null && actorSpecials.size() > 0)
            {
                for (Special special : actorSpecials.values())
                {
                    if (actor.getSpecialCurrent() >= special.getCost())
                    {
                        specialTickets = actor.getSpecialTickets();
                        break;
                    }
                }
            }

            ConcurrentHashMap<Integer, Item> items = actor.getItems();
            if (items.size() > 0)
            {
                Logger.logI("items:<" + items.size() + ">");
                for (int id : items.keySet())
                {
                    Item item = items.get(id);
                    if (item != null && item instanceof ItemConsumable)
                    {
                        useItemTickets = actor.getUseItemTickets();
                        itemId = id;
                        Logger.logI("selected item:<" + itemId + ">");
                        break;
                    }
                }
            }

        }
        else
        {
            Logger.logI("no player targets found");
        }

        int                moveTickets    = 0;
        Room               room           = GameMaster.getActorRoom(model, actorId);
        ArrayList<Integer> validMoveRooms = new ArrayList<>();
        if (room != null && room.isPlaced())
        {
            ArrayList<Integer> adjacentRooms = model.getMap().getAdjacentRooms(actor.getRoom());
            if (adjacentRooms != null && adjacentRooms.size() > 0)
            {
                boolean foundValidMove = false;
                for (int roomId : adjacentRooms)
                {
                    if (GameMaster.getRoomFull(model, roomId) == 0 &&
                        GameMaster.getValidPath(model, actor.getRoom(), roomId) == 0)
                    {
                        validMoveRooms.add(roomId);
                        foundValidMove = true;
                    }
                }
                if (foundValidMove)
                {
                    moveTickets = actor.getMoveTickets();
                }
            }
            if (playerTargets.size() == 0 && actor.isSeeker())
            {
                moveTickets = 1;
            }
        }

        Logger.logI("attack tickets: " + attackTickets, 1);
        Logger.logI("defend tickets: " + defendTickets, 1);
        Logger.logI("special tickets: " + specialTickets, 1);
        Logger.logI("use item tickets: " + useItemTickets, 1);
        Logger.logI("move tickets: " + moveTickets, 1);

        String action =
            Lottery.performLottery(new String[]{"attack", "defend", "special", "item", "move"},
                                   new int[]{attackTickets, defendTickets, specialTickets, useItemTickets, moveTickets});

        if (action != null)
        {
            switch (action)
            {
                case "attack":
                    Collections.shuffle(playerTargets);

                    GameMaster.attack(model, actorId, playerTargets.get(0));

                    Logger.logI("actor " + actor.getName() + " attacked " +
                                GameMaster.getActor(model, playerTargets.get(0)).getName());

                    gameController.feedback(
                        GameMaster.getActor(model, actorId).getDisplayName() + " attacked " +
                        GameMaster.getActor(model, playerTargets.get(0)).getDisplayName());

                    gameController.onActorAction(actorId,
                                                 playerTargets.get(0),
                                                 "attack",
                                                 actor.getName());
                    break;
                case "defend":
                    actor.setState(Actor.E_STATE.DEFEND);

                    gameController.feedback(
                        GameMaster.getActor(model, actorId).getDisplayName() + " defended ");

                    gameController.onActorAction(actorId, -1, "defend", actor.getName());
                    break;
                case "special":
                    ArrayList<Integer> specialIds = new ArrayList<>();
                    for (Integer specialId : actorSpecials.keySet())
                    {
                        specialIds.add(specialId);
                    }
                    Collections.shuffle(specialIds);

                    boolean usedSpecial = false;

                    for (Integer specialId : specialIds)
                    {
                        Special special = actorSpecials.get(specialId);

                        if (special.getCost() <= actor.getSpecialCurrent())
                        {
                            switch (special.getTargetingType())
                            {
                                case SINGLE_PLAYER:
                                    if (nonPlayerTargets.size() > 0)
                                    {
                                        Collections.shuffle(nonPlayerTargets);

                                        actor.performSpecial(special,
                                                             GameMaster.getActor(model,
                                                                                 nonPlayerTargets.get(
                                                                                     0)));

                                        Logger.logI(
                                            "actor " + actor.getName() + " used " +
                                            special.getName() +
                                            " on " +
                                            GameMaster.getActor(model, nonPlayerTargets.get(0))
                                                      .getName());

                                        gameController.feedback(
                                            actor.getDisplayName() + " used " +
                                            special.getDisplayName() +
                                            " on " +
                                            GameMaster.getActor(model,
                                                                nonPlayerTargets.get(0))
                                                      .getDisplayName());

                                        gameController.onActorAction(actorId,
                                                                     nonPlayerTargets.get(0),
                                                                     "special.help",
                                                                     special.getName());

                                        usedSpecial = true;
                                    }
                                    break;
                                case SINGLE_NON_PLAYER:
                                    Collections.shuffle(playerTargets);

                                    actor.performSpecial(special,
                                                         GameMaster.getActor(model,
                                                                             playerTargets.get(0)));

                                    Logger.logI(
                                        "actor " + actor.getName() + " used " + special.getName() +
                                        " on " +
                                        GameMaster.getActor(model, playerTargets.get(0)).getName());

                                    gameController.feedback(
                                        actor.getDisplayName() + " used " +
                                        special.getDisplayName() +
                                        " on " +
                                        GameMaster.getActor(model, playerTargets.get(0))
                                                  .getDisplayName());

                                    gameController.onActorAction(actorId,
                                                                 playerTargets.get(0),
                                                                 "special.harm",
                                                                 special.getName());

                                    usedSpecial = true;
                                    break;
                                case AOE_PLAYER:
                                    if (nonPlayerTargets.size() > 0)
                                    {
                                        ArrayList<Actor> nonPlayerTargetActors = new ArrayList<>();
                                        for (int i : nonPlayerTargets)
                                        {
                                            nonPlayerTargetActors.add(GameMaster.getActor(model,
                                                                                          i));
                                        }

                                        actor.performSpecial(special, nonPlayerTargetActors);

                                        Logger.logI(
                                            "actor " + actor.getName() + " used " +
                                            special.getName());

                                        gameController.feedback(
                                            actor.getDisplayName() + " used " +
                                            special.getDisplayName());

                                        gameController.onActorAction(actorId,
                                                                     -1,
                                                                     "special.help",
                                                                     special.getName());

                                        usedSpecial = true;
                                    }
                                    break;
                                case AOE_NON_PLAYER:
                                    ArrayList<Actor> playerTargetActors = new ArrayList<>();
                                    for (int i : playerTargets)
                                    {
                                        playerTargetActors.add(GameMaster.getActor(model, i));
                                    }

                                    actor.performSpecial(special, playerTargetActors);

                                    Logger.logI(
                                        "actor " + actor.getName() + " used " + special.getName());

                                    gameController.feedback(
                                        actor.getDisplayName() + " used " +
                                        special.getDisplayName());

                                    gameController.onActorAction(actorId,
                                                                 -1,
                                                                 "special.harm",
                                                                 special.getName());

                                    usedSpecial = true;

                                    break;
                            }
                        }

                        if (usedSpecial)
                        {
                            break;
                        }
                    }

                    if (!usedSpecial)
                    {
                        Logger.logD("actor <" + actorId + "> failed to use special");

                        //takeTurn(gameController, model, actorId);

                        gameController.turnPassed(actorId);
                    }
                    break;
                case "item":
                    ItemConsumable item = (ItemConsumable) GameMaster.getItem(model, itemId);

                    String itemName = item.getDisplayName();

                    Logger.logI("using item " + itemName);

                    boolean usedItem = false;

                    switch (item.getTargetingType())
                    {
                        case SINGLE_PLAYER:
                        {
                            if (nonPlayerTargets.size() > 0)
                            {
                                Collections.shuffle(nonPlayerTargets);

                                Actor targetActor =
                                    GameMaster.getActor(model, nonPlayerTargets.get(0));

                                targetActor.useItem(model, itemId);

                                actor.removeItem(itemId);

                                Logger.logI(
                                    "actor " + actor.getName() + " used " + item.getName());

                                gameController.onActorAction(actorId,
                                                             nonPlayerTargets.get(0),
                                                             "item.help",
                                                             item.getName());

                                gameController.feedback(
                                    actor.getDisplayName() + " used " + itemName + " on " +
                                    ((actor.getId() ==
                                      targetActor.getId()) ? "himself" : targetActor.getDisplayName()));

                                usedItem = true;
                            }
                            break;
                        }
                        case SINGLE_NON_PLAYER:
                        {
                            Collections.shuffle(playerTargets);

                            Actor targetActor = GameMaster.getActor(model, playerTargets.get(0));

                            targetActor.useItem(model, itemId);

                            actor.removeItem(itemId);

                            Logger.logI(
                                "actor " + actor.getName() + " used " + item.getName());

                            gameController.onActorAction(actorId,
                                                         playerTargets.get(0),
                                                         "item.harm",
                                                         item.getName());

                            gameController.feedback(
                                actor.getDisplayName() + " used " + itemName + " on " +
                                targetActor.getDisplayName());

                            usedItem = true;

                            break;
                        }
                        case AOE_PLAYER:
                        {
                            if (nonPlayerTargets.size() > 0)
                            {
                                for (int i : nonPlayerTargets)
                                {
                                    Actor targetActor = GameMaster.getActor(model, i);

                                    targetActor.useItem(model, itemId);
                                }

                                actor.removeItem(itemId);

                                Logger.logI(
                                    "actor " + actor.getName() + " used " + item.getName());

                                gameController.onActorAction(actorId,
                                                             -1,
                                                             "item.help",
                                                             item.getName());

                                gameController.feedback(
                                    actor.getDisplayName() + " used " + itemName);

                                usedItem = true;
                            }
                            break;
                        }
                        case AOE_NON_PLAYER:
                        {
                            for (int i : playerTargets)
                            {
                                Actor targetActor = GameMaster.getActor(model, i);

                                targetActor.useItem(model, itemId);
                            }

                            actor.removeItem(itemId);

                            Logger.logI(
                                "actor " + actor.getName() + " used " + item.getName());

                            gameController.onActorAction(actorId, -1, "item.harm", item.getName());

                            gameController.feedback(actor.getDisplayName() + " used " + itemName);

                            usedItem = true;

                            break;
                        }
                    }

                    /*if (actor.useItem(itemId))
                    {
                        GameMaster.removeItem(model, itemId);
                    }*/

                    if (usedItem)
                    {
                        actor.setState(Actor.E_STATE.NEUTRAL);
                    }
                    else
                    {
                        Logger.logI("actor <" + actorId + "> failed to use item");

                        //takeTurn(gameController, model, actorId);

                        gameController.turnPassed(actorId);
                    }

                    break;
                case "move":
                    if (validMoveRooms.size() > 0)
                    {
                        Collections.shuffle(validMoveRooms);

                        Logger.logI(
                            "actor " + actor.getName() + " moved to " + validMoveRooms.get(0));

                        gameController.onActorMove(actorId, validMoveRooms.get(0));
                    }
                    else
                    {
                        Logger.logI("actor <" + actorId + "> failed to move");

                        gameController.turnPassed(actorId);
                    }
                    break;
                default:
                    //actor can't do anything, should only be the case if actor is in an
                    //unplaced room

                    Logger.logI(
                        "actor " + actorId + ":" + actor.getName() + " was unable to act");

                    gameController.turnPassed(actorId);
                    break;
            }
        }
        else
        {
            //actor can't do anything, should only be the case if actor is in an
            //unplaced room

            Logger.logI(
                "actor " + actorId + ":" + actor.getName() + " was unable to act");

            gameController.turnPassed(actorId);
        }

        /*int totalTickets = attackTickets + defendTickets + specialTickets + moveTickets;

        int ticket = (int) (Math.random() * totalTickets);

        Logger.logI("ticket selected: " + ticket, 1);

        if (attackTickets != 0 && ticket <= attackTickets)
        {
            Collections.shuffle(playerTargets);

            GameMaster.attack(model, actorId, playerTargets.get(0));

            Logger.logI("actor " + actor.getName() + " attacked " +
                        GameMaster.getActor(model, playerTargets.get(0)).getName());

            gameController.feedback(
                GameMaster.getActor(model, actorId).getDisplayName() + " attacked " +
                GameMaster.getActor(model, playerTargets.get(0)).getDisplayName());

            gameController.onActorAction(actorId, playerTargets.get(0), "attack");
        }
        else if (defendTickets != 0 && ticket <= attackTickets + defendTickets)
        {
            gameController.feedback(
                GameMaster.getActor(model, actorId).getDisplayName() + " defended ");

            gameController.onActorAction(actorId, -1, "defend");
        }
        else if (specialTickets != 0 && ticket < attackTickets + defendTickets + specialTickets)
        {
            ArrayList<Integer> specialIds = new ArrayList<>();
            for (Integer specialId : actorSpecials.keySet())
            {
                specialIds.add(specialId);
            }
            Collections.shuffle(specialIds);

            boolean usedSpecial = false;

            for (Integer specialId : specialIds)
            {
                Special special = actorSpecials.get(specialId);

                if (special.getCost() <= actor.getSpecialCurrent())
                {
                    switch (special.getTargetingType())
                    {
                        case SINGLE_PLAYER:
                            if (nonPlayerTargets.size() > 0)
                            {
                                Collections.shuffle(nonPlayerTargets);

                                actor.performSpecial(special,
                                                     GameMaster.getActor(model,
                                                                         playerTargets.get(0)));

                                Logger.logI(
                                    "actor " + actor.getName() + " used " + special.getName() +
                                    " on " +
                                    GameMaster.getActor(model, nonPlayerTargets.get(0)).getName());

                                gameController.feedback(actor.getDisplayName() + " used special " +
                                                        special.getDisplayName() +
                                                        " on " +
                                                        GameMaster.getActor(model,
                                                                            nonPlayerTargets.get(0))
                                                                  .getDisplayName());

                                gameController.onActorAction(actorId,
                                                             nonPlayerTargets.get(0),
                                                             "special.help");

                                usedSpecial = true;
                            }
                            break;
                        case SINGLE_NON_PLAYER:
                            Collections.shuffle(playerTargets);

                            actor.performSpecial(special,
                                                 GameMaster.getActor(model, playerTargets.get(0)));

                            Logger.logI(
                                "actor " + actor.getName() + " used " + special.getName() +
                                " on " +
                                GameMaster.getActor(model, playerTargets.get(0)).getName());

                            gameController.feedback(actor.getDisplayName() + " used special " +
                                                    special.getDisplayName() +
                                                    " on " +
                                                    GameMaster.getActor(model, playerTargets.get(0))
                                                              .getDisplayName());

                            gameController.onActorAction(actorId,
                                                         playerTargets.get(0),
                                                         "special.harm");

                            usedSpecial = true;
                            break;
                        case AOE_PLAYER:
                            if (nonPlayerTargets.size() > 0)
                            {
                                ArrayList<Actor> nonPlayerTargetActors = new ArrayList<>();
                                for (int i : nonPlayerTargets)
                                {
                                    nonPlayerTargetActors.add(GameMaster.getActor(model, i));
                                }

                                actor.performSpecial(special, nonPlayerTargetActors);

                                Logger.logI(
                                    "actor " + actor.getName() + " used " + special.getName());

                                gameController.feedback(actor.getDisplayName() + " used special " +
                                                        special.getDisplayName());

                                gameController.onActorAction(actorId,
                                                             -1,
                                                             "special.help");

                                usedSpecial = true;
                            }
                            break;
                        case AOE_NON_PLAYER:
                            ArrayList<Actor> playerTargetActors = new ArrayList<>();
                            for (int i : playerTargets)
                            {
                                playerTargetActors.add(GameMaster.getActor(model, i));
                            }

                            actor.performSpecial(special, playerTargetActors);

                            Logger.logI(
                                "actor " + actor.getName() + " used " + special.getName());

                            gameController.feedback(actor.getDisplayName() + " used special " +
                                                    special.getDisplayName());

                            gameController.onActorAction(actorId,
                                                         -1,
                                                         "special.harm");

                            usedSpecial = true;

                            break;
                    }
                }

                if (usedSpecial)
                {
                    break;
                }
            }

            if (!usedSpecial)
            {
                Logger.logD("actor <" + actorId + "> failed to use special");

                gameController.turnPassed(actorId);
            }
        }
        else if (moveTickets != 0 &&
                 ticket < attackTickets + defendTickets + specialTickets + moveTickets)
        {
            if (validMoveRooms.size() > 0)
            {
                Collections.shuffle(validMoveRooms);

                Logger.logI(
                    "actor " + actor.getName() + " moved to " + validMoveRooms.get(0));

                gameController.onActorMove(actorId, validMoveRooms.get(0));
            }
        }
        else
        {
            //actor can't do anything, should only be the case if actor is in an
            //unplaced room

            Logger.logI(
                "actor " + actorId + ":" + actor.getName() + " was unable to act");

            gameController.turnPassed(actorId);
        }
        */
    }
}