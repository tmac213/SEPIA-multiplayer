package edu.cwru.sepia.agent.minimax;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;
import edu.cwru.sepia.util.DistanceMetrics;
import edu.cwru.sepia.util.Pair;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class stores all of the information the agent
 * needs to know about the state of the game. For example this
 * might include things like footmen HP and positions.
 *
 * Add any information or methods you would like to this class,
 * but do not delete or change the signatures of the provided methods.
 */
public class GameState {

    private static final int OBSERVER_ID = -999;

    private static final int WEIGHT_FOOTMAN_HP = 1;
    private static final int WEIGHT_ARCHER_HP = -5;
    private static final int WEIGHT_FOOTMAN_ALIVE = 10;
    private static final int WEIGHT_ARCHER_ALIVE = -20;
    private static final int WEIGHT_FOOTMAN_ARCHER_DISTANCE = -1000;

    private static final Direction[] VALID_DIRECTIONS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    State.StateView state;
    private Map<Integer, Pair<Integer, Integer>> footmanLocations = new HashMap<>();
    private Map<Integer, Pair<Integer, Integer>> archerLocations = new HashMap<>();
    private Set<Pair<Integer, Integer>> resourceLocations = new HashSet<>();
    private int depth;

    /**
     * You will implement this constructor. It will
     * extract all of the needed state information from the built in
     * SEPIA state view.
     *
     * You may find the following state methods useful:
     *
     * state.getXExtent() and state.getYExtent(): get the map dimensions
     * state.getAllResourceIDs(): returns all of the obstacles in the map
     * state.getResourceNode(Integer resourceID): Return a ResourceView for the given ID
     *
     * For a given ResourceView you can query the position using
     * resource.getXPosition() and resource.getYPosition()
     *
     * For a given unit you will need to find the attack damage, range and max HP
     * unitView.getTemplateView().getRange(): This gives you the attack range
     * unitView.getTemplateView().getBasicAttack(): The amount of damage this unit deals
     * unitView.getTemplateView().getBaseHealth(): The maximum amount of health of this unit
     *
     * @param state Current state of the episode
     */
    public GameState(State.StateView state) {
        this.state = state;

        buildLocationMaps(state);

        resourceLocations.addAll(state.getAllResourceNodes().stream()
                .map(resource -> new Pair<>(resource.getXPosition(), resource.getYPosition()))
                .collect(Collectors.toList()));

        this.depth = state.getTurnNumber();
    }

    public GameState(GameState parent, Map<Integer, Action> actionsTaken) {
        try {
            this.state = resultingState(parent.state.getStateCreator().createState(), actionsTaken);
        } catch (IOException e) {
            e.printStackTrace();
        }

        buildLocationMaps(this.state);

        this.resourceLocations = parent.resourceLocations;
        this.depth = parent.depth + 1;
    }

    private void buildLocationMaps(State.StateView state) {
        // add units to respective lists
        for (Unit.UnitView unit : state.getAllUnits()) {
            switch (unit.getTemplateView().getName()) {
                case "Footman":
                    this.footmanLocations.put(unit.getID(), new Pair<>(unit.getXPosition(), unit.getYPosition()));
                    System.out.print("added footman ");
                    break;
                case "Archer":
                    this.archerLocations.put(unit.getID(), new Pair<>(unit.getXPosition(), unit.getYPosition()));
                    System.out.print("added archer ");
                    break;
                default:
                    System.out.print("unknown unit ");
                    break;
            }
            System.out.println(unit.toString());
        }
    }

    private State.StateView resultingState(State state, Map<Integer, Action> actionsTaken) {

        for (Map.Entry<Integer, Action> actionEntry : actionsTaken.entrySet()) {
            switch (actionEntry.getValue().getType()) {
                case PRIMITIVEMOVE:
                    state.moveUnit(state.getUnit(actionEntry.getKey()), ((DirectedAction) actionEntry.getValue()).getDirection());
                    break;
                case PRIMITIVEATTACK:
                    TargetedAction attackAction = (TargetedAction) actionEntry.getValue();
                    Unit attacker = state.getUnit(attackAction.getUnitId());
                    Unit victim = state.getUnit(attackAction.getTargetId());
                    int temp;
                    victim.setHP((temp = victim.getCurrentHealth() - attacker.getTemplate().getBasicAttack()) > 0 ? temp : 0);
                    if (victim.getCurrentHealth() == 0) {
                        state.removeUnit(victim.ID);
                    }
                    break;
                default:
                    System.err.println("unexpected action in resultingState()");
            }
        }

        return state.getView(OBSERVER_ID);
    }

    public boolean isTerminal() {
        return footmanLocations.isEmpty() || archerLocations.isEmpty();
    }

    /**
     * You will implement this function.
     *
     * You should use weighted linear combination of features.
     * The features may be primitives from the state (such as hp of a unit)
     * or they may be higher level summaries of information from the state such
     * as distance to a specific location. Come up with whatever features you think
     * are useful and weight them appropriately.
     *
     * It is recommended that you start simple until you have your algorithm working. Then watch
     * your agent play and try to add features that correct mistakes it makes. However, remember that
     * your features should be as fast as possible to compute. If the features are slow then you will be
     * able to do less plys in a turn.
     *
     * Add a good comment about what is in your utility and why you chose those features.
     *
     * @return The weighted linear combination of the features
     */
    public double getUtility() {

        int footmenHP = footmanLocations.keySet().stream()
                .mapToInt(id -> state.getUnit(id).getHP())
                .sum();

        int archersHP = archerLocations.keySet().stream()
                .mapToInt(id -> state.getUnit(id).getHP())
                .sum();

        int footmenAlive = footmanLocations.keySet().stream()
                .mapToInt(id -> state.getUnit(id).getHP() > 0 ? 1 : 0)
                .sum();

        int archersAlive = archerLocations.keySet().stream()
                .mapToInt(id -> state.getUnit(id).getHP() > 0 ? 1 : 0)
                .sum();

        double averageFootmanToArcherDistance = footmanLocations.keySet().stream()
                .mapToDouble(this::nearestArcherDistance)
                .sum() / (footmenAlive > 0 ? footmenAlive : 1);

        return WEIGHT_FOOTMAN_HP * footmenHP +
                WEIGHT_ARCHER_HP * archersHP +
                WEIGHT_FOOTMAN_ALIVE * footmenAlive +
                WEIGHT_ARCHER_ALIVE * archersAlive +
                WEIGHT_FOOTMAN_ARCHER_DISTANCE * averageFootmanToArcherDistance;
    }

    private double nearestArcherDistance(int id) {
        Unit.UnitView unit = state.getUnit(id);
        double distance = 0;

        if (unit.getHP() <= 0) {
            return distance;
        }

        for (Pair<Integer, Integer> archerLocation : archerLocations.values()) {
            double temp = DistanceMetrics.euclideanDistance(unit.getXPosition(), unit.getYPosition(), archerLocation.a, archerLocation.b);
            distance = temp > distance ? temp : distance;
        }

        return distance;
    }

    /**
     * You will implement this function.
     *
     * This will return a list of GameStateChild objects. You will generate all of the possible
     * actions in a step and then determine the resulting game state from that action. These are your GameStateChildren.
     *
     * You may find it useful to iterate over all the different directions in SEPIA.
     *
     * for(Direction direction : Directions.values())
     *
     * To get the resulting position from a move in that direction you can do the following
     * x += direction.xComponent()
     * y += direction.yComponent()
     *
     * @return All possible actions and their associated resulting game state
     */
    public List<GameStateChild> getChildren() {
        List<GameStateChild> ret = new LinkedList<>();

        Map<Integer, List<Action>> validActions;

        if (depth % 2 == 0) {
            validActions = validActions(footmanLocations.keySet(), archerLocations.keySet());
        } else {
            validActions = validActions(archerLocations.keySet(), footmanLocations.keySet());
        }

        ret.addAll(getActionCombinations(validActions).stream()
                .filter(validActionMap -> !actionsConflict(validActionMap))
                .map(actionCombination -> new GameStateChild(actionCombination, new GameState(this, actionCombination)))
                .collect(Collectors.toList()));

        return ret;
    }

    private List<Map<Integer, Action>> getActionCombinations(Map<Integer, List<Action>> validActions) {

        int currentUnitID = validActions.keySet().iterator().next();
        List<Map<Integer, Action>> ret = new LinkedList<>();

        if (validActions.keySet().size() == 1) {
            for (Action action : validActions.get(currentUnitID)) {
                Map<Integer, Action> newMap = new HashMap<>();
                newMap.put(currentUnitID, action);
                ret.add(newMap);
            }

        } else {
            List<Action> currentUnitActions = validActions.remove(currentUnitID);
            List<Map<Integer, Action>> actionCombinationsWithoutCurrentUnit = getActionCombinations(validActions);

            for (Action currentUnitAction : currentUnitActions) {
                for (Map<Integer, Action> actionCombination : actionCombinationsWithoutCurrentUnit) {
                    Map<Integer, Action> newMap = new HashMap<>(actionCombination);
                    newMap.put(currentUnitID, currentUnitAction);
                    ret.add(newMap);
                }
            }
        }

        return ret;
    }

    // return map from unit ID to its valid actions
    private Map<Integer, List<Action>> validActions(Collection<Integer> unitIDs, Collection<Integer> enemyIDs) {
        Map<Integer, List<Action>> ret = new HashMap<>();

        for (int currentUnitID : unitIDs) {
            List<Action> validActionsForCurrentUnit = new LinkedList<>();

            for (Direction direction : VALID_DIRECTIONS) {
                if (validMove(currentUnitID, direction)) {
                    validActionsForCurrentUnit.add(Action.createPrimitiveMove(currentUnitID, direction));
                }
            }

            validActionsForCurrentUnit.addAll(enemyIDs.stream()
                    .filter(enemyID -> validAttack(currentUnitID, enemyID))
                    .map(enemyID -> Action.createPrimitiveAttack(currentUnitID, enemyID))
                    .collect(Collectors.toList()));

            ret.put(currentUnitID, validActionsForCurrentUnit);
        }

        return ret;
    }

    private boolean actionsConflict(Map<Integer, Action> moves) {
        Set<Pair<Integer, Integer>> resultLocations = new HashSet<>();

        for (Map.Entry<Integer, Action> move : moves.entrySet()) {
            if (move.getValue().getType() == ActionType.PRIMITIVEMOVE
                    && !resultLocations.add(resultingLocation(move.getKey(), (DirectedAction) move.getValue()))) {
                return true;
            }
        }

        return false;
    }

    private Pair<Integer, Integer> resultingLocation(int id, DirectedAction action) {
        return new Pair<>(state.getUnit(id).getXPosition() + action.getDirection().xComponent(),
                state.getUnit(id).getYPosition() + action.getDirection().yComponent());
    }

    private boolean validMove(int unitID, Direction direction) {
        Pair<Integer, Integer> newLocation = new Pair<>(
                this.state.getUnit(unitID).getXPosition() + direction.xComponent(),
                this.state.getUnit(unitID).getYPosition() + direction.yComponent());

        return !resourceLocations.contains(newLocation) && !this.state.isUnitAt(newLocation.a, newLocation.b);
    }

    private boolean validAttack(int unitID, int enemyID) {
        return movesBetween(unitID, enemyID) <= 1;
    }

    private int movesBetween(int aID, int bID) {
        return Math.abs(this.state.getUnit(aID).getXPosition() - this.state.getUnit(bID).getXPosition())
                + Math.abs(this.state.getUnit(aID).getYPosition() - this.state.getUnit(bID).getYPosition());
    }
}
