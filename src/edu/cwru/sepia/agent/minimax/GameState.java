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

    private static final int WEIGHT_FOOTMAN_HP = 1;
    private static final int WEIGHT_ARCHER_HP = -3;
    private static final int WEIGHT_FOOTMAN_ALIVE = 20;
    private static final int WEIGHT_ARCHER_ALIVE = -20;
    private static final int WEIGHT_FOOTMAN_ARCHER_DISTANCE = -5;
    private static final int WEIGHT_TREES_BLOCKING = -5;

    private static final List<Direction> VALID_DIRECTIONS = Arrays.asList(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    private Map<Integer, GameUnit> units;  // footmen and archers
    private Set<GameUnit> footmen;
    private Set<GameUnit> archers;
    private Map<Integer, Pair<Integer, Integer>> footmanLocations;
    private Map<Integer, Pair<Integer, Integer>> archerLocations;
    private Set<Pair<Integer, Integer>> resourceLocations;
    private int xExtent;
    private int yExtent;
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
        this.initialize(state);

        this.xExtent = state.getXExtent();
        this.yExtent = state.getYExtent();
        this.depth = state.getTurnNumber();
    }

    public GameState(GameState parent, Map<Integer, Action> actionsTaken) {
        this.initialize(parent);
        this.applyActions(actionsTaken);

        this.xExtent = parent.xExtent;
        this.yExtent = parent.yExtent;
        this.depth = parent.depth + 1;
    }

    private void initialize(State.StateView state) {

        this.units = new HashMap<>();
        this.footmen = new HashSet<>();
        this.archers = new HashSet<>();
        this.footmanLocations = new HashMap<>();
        this.archerLocations = new HashMap<>();

        for (Unit.UnitView unit : state.getAllUnits()) {
            switch (unit.getTemplateView().getName()) {
                case "Footman":
                    GameUnit newFootman = new GameUnit(unit);
                    this.units.put(newFootman.id, newFootman);
                    this.footmen.add(newFootman);
                    this.footmanLocations.put(newFootman.id, newFootman.location);
                    break;
                case "Archer":
                    GameUnit newArcher = new GameUnit(unit);
                    this.units.put(newArcher.id, newArcher);
                    this.archers.add(newArcher);
                    this.archerLocations.put(newArcher.id, newArcher.location);
                    break;
                default:
                    System.err.print("unknown unit in initialize()");
                    break;
            }
        }

        this.resourceLocations = state.getAllResourceNodes().stream()
                .map(resource -> new Pair<>(resource.getXPosition(), resource.getYPosition()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void initialize(GameState parent) {

        this.footmen = new HashSet<>();
        this.archers = new HashSet<>();

        this.units = parent.units.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new GameUnit(e.getValue())));

        for (Map.Entry<Integer, GameUnit> entry : this.units.entrySet()) {
            if (entry.getValue().name.equals("Footman")) {
                this.footmen.add(entry.getValue());
            } else {
                this.archers.add(entry.getValue());
            }
        }

        this.footmanLocations = this.footmen.stream()
                .collect(Collectors.toMap(
                        footman -> footman.id,
                        footman -> footman.location)
                );
        this.archerLocations = this.archers.stream()
                .collect(Collectors.toMap(
                        archer -> archer.id,
                        archer -> archer.location)
                );

        this.resourceLocations = parent.resourceLocations;
    }

    private void applyActions(Map<Integer, Action> actionsTaken) {

        for (Map.Entry<Integer, Action> actionEntry : actionsTaken.entrySet()) {
            switch (actionEntry.getValue().getType()) {
                case PRIMITIVEMOVE:
                    performMove(units.get(actionEntry.getKey()), ((DirectedAction) actionEntry.getValue()).getDirection());
                    break;
                case PRIMITIVEATTACK:
                    TargetedAction attackAction = (TargetedAction) actionEntry.getValue();
                    GameUnit attacker = units.get(attackAction.getUnitId());
                    GameUnit victim = units.get(attackAction.getTargetId());
                    performAttack(attacker, victim);
                    break;
                default:
                    System.err.println("unexpected action in applyActions()");
            }
        }
    }

    private void performMove(GameUnit unit, Direction direction) {
        unit.location.a = unit.location.a + direction.xComponent();
        unit.location.b = unit.location.b + direction.yComponent();
    }

    private void performAttack(GameUnit attacker, GameUnit victim) {
        if (victim == null) {
            return;  // the previous footman's attack already killed the archer
        }
        int temp;
        victim.hp = (temp = victim.hp - attacker.attackDamage) > 0 ? temp : 0;
        if (victim.hp == 0) {
            removeUnitFromGameState(victim);
        }
    }

    private void removeUnitFromGameState(GameUnit unit) {
        this.units.remove(unit.id);
        this.footmen.remove(unit);
        this.archers.remove(unit);
        this.footmanLocations.remove(unit.id);
        this.archerLocations.remove(unit.id);
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

        int footmenHP = footmen.stream()
                .mapToInt(u -> u.hp)
                .sum();

        int archersHP = archers.stream()
                .mapToInt(u -> u.hp)
                .sum();

        int footmenAlive = footmen.stream()
                .mapToInt(footman -> footman.hp > 0 ? 1 : 0)
                .sum();

        int archersAlive = archers.stream()
                .mapToInt(archer -> archer.hp > 0 ? 1 : 0)
                .sum();

        double averageFootmanToArcherDistance = footmen.stream()
                .mapToDouble(footman -> distanceBetween(footman.location, nearestArcherLocation(footman)))
                .sum() / ((footmenAlive > 0 && archersAlive > 0) ? footmenAlive : 1);

        double averageNumTreesBetweenFootmenAndArchers = footmen.stream()
                .mapToDouble(this::numTreesBetweenNearestArcher)
                .sum() / ((footmenAlive > 0 && archersAlive > 0) ? footmenAlive : 1);

        return WEIGHT_FOOTMAN_HP * footmenHP +
                WEIGHT_ARCHER_HP * archersHP +
                WEIGHT_FOOTMAN_ALIVE * footmenAlive +
                WEIGHT_ARCHER_ALIVE * archersAlive +
                WEIGHT_FOOTMAN_ARCHER_DISTANCE * averageFootmanToArcherDistance +
                WEIGHT_TREES_BLOCKING * averageNumTreesBetweenFootmenAndArchers;
    }

    private double distanceBetween(Pair<Integer, Integer> l1, Pair<Integer, Integer> l2) {
        return DistanceMetrics.euclideanDistance(l1.a, l1.b, l2.a, l2.b);
    }

    private Pair<Integer, Integer> nearestArcherLocation(GameUnit footman) {
        double nearestArcherDistance = 0;
        Pair<Integer, Integer> ret = footman.location;

        for (Pair<Integer, Integer> archerLocation : archerLocations.values()) {
            double temp = DistanceMetrics.euclideanDistance(footman.xPosition(), footman.yPosition(), archerLocation.a, archerLocation.b);
            if (nearestArcherDistance == 0 || temp < nearestArcherDistance) {
                nearestArcherDistance = temp;
                ret = archerLocation;
            }
        }
        // return the distance from footman to the nearest archer
        return ret;
    }

    private double numTreesBetweenNearestArcher(GameUnit footman) {
        double ret = 0.0;

        if (resourceLocations.isEmpty()) {
            return ret;
        }

        Pair<Integer, Integer> nearestArcherLocation = nearestArcherLocation(footman);

        for (int i = Math.min(nearestArcherLocation.a, footman.location.a);
             i < Math.max(nearestArcherLocation.a, footman.location.a);
             ++i) {
            for (int j = Math.min(nearestArcherLocation.b, footman.location.b);
                    j < Math.max(nearestArcherLocation.b, footman.location.b);
                    ++j) {
                if (resourceLocations.contains(new Pair<>(i, j))) {
                    ++ret;
                }
            }
        }
        // return the number of trees in the area between the nearest archer to the footman
        return ret;
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
    public List<GameStateChild> getChildren(boolean isMax) {
        List<GameStateChild> ret = new LinkedList<>();
        Map<Integer, List<Action>> validActions;

        if (isMax) {
            validActions = validActions(footmanLocations.keySet(), archerLocations.keySet());
        } else {
            validActions = validActions(archerLocations.keySet(), footmanLocations.keySet());
        }

        ret.addAll(getActionCombinations(validActions).stream()
                // filter out two units moving to the same space
                .filter(validActionMap -> !actionsConflict(validActionMap))
                // create a GameStateChild with the resulting GameState from each Action combination
                .map(actionCombination -> new GameStateChild(actionCombination, new GameState(this, actionCombination)))
                .collect(Collectors.toList()));

        return ret;
    }

    // return map from unit ID to its valid actions
    private Map<Integer, List<Action>> validActions(Collection<Integer> unitIDs, Collection<Integer> enemyIDs) {
        Map<Integer, List<Action>> ret = new HashMap<>();

        for (int currentUnitID : unitIDs) {
            // for each unit
            List<Action> validActionsForCurrentUnit = new LinkedList<>();

            // add all valid moves
            validActionsForCurrentUnit.addAll(VALID_DIRECTIONS.stream()
                    .filter(direction -> validMove(currentUnitID, direction))
                    .map(direction -> Action.createPrimitiveMove(currentUnitID, direction))
                    .collect(Collectors.toList()));

            // and all valid attacks
            validActionsForCurrentUnit.addAll(enemyIDs.stream()
                    .filter(enemyID -> validAttack(currentUnitID, enemyID))
                    .map(enemyID -> Action.createPrimitiveAttack(currentUnitID, enemyID))
                    .collect(Collectors.toList()));

            ret.put(currentUnitID, validActionsForCurrentUnit);
        }

        return ret;
    }

    private List<Map<Integer, Action>> getActionCombinations(Map<Integer, List<Action>> validActions) {

        int currentUnitID = validActions.keySet().iterator().next();
        List<Map<Integer, Action>> ret = new LinkedList<>();

        if (validActions.keySet().size() == 1) {
            // if this is the last unit ID
            for (Action action : validActions.get(currentUnitID)) {
                // for each action the current unit can take
                Map<Integer, Action> newMap = new HashMap<>();  // create a new map
                newMap.put(currentUnitID, action);  // and put the ID -> action combo pair in there
                ret.add(newMap);  // then add it to the return list
            }

        } else {
            // else, remove the first ID and recurse on the rest of the IDs
            List<Action> currentUnitActions = validActions.remove(currentUnitID);
            List<Map<Integer, Action>> actionCombinationsWithoutCurrentUnit = getActionCombinations(validActions);

            // we now have a list of valid combinations with the n - 1 IDs and their action combinations (currentUnitID is the nth)
            for (Action currentUnitAction : currentUnitActions) {
                // for each action the current unit can take
                for (Map<Integer, Action> actionCombination : actionCombinationsWithoutCurrentUnit) {
                    // for each action combination with n - 1 IDs
                    Map<Integer, Action> newMap = new HashMap<>(actionCombination);  // create a copy of the map
                    newMap.put(currentUnitID, currentUnitAction);  // and add the current ID -> action pair
                    ret.add(newMap);  // then add the resulting combination to the return list
                }
            }
        }

        return ret;
    }

    private boolean actionsConflict(Map<Integer, Action> moves) {
        Set<Pair<Integer, Integer>> resultLocations = new HashSet<>();

        for (Map.Entry<Integer, Action> move : moves.entrySet()) {
            if (move.getValue().getType() == ActionType.PRIMITIVEMOVE
                    && !resultLocations.add(resultingLocation(move.getKey(), (DirectedAction) move.getValue()))) {
                return true;  // if adding to the set returns false (the value was already there) then the moves conflict
            }
        }

        return false;
    }

    private Pair<Integer, Integer> resultingLocation(int id, DirectedAction action) {
        return new Pair<>(this.units.get(id).xPosition() + action.getDirection().xComponent(),
                this.units.get(id).yPosition() + action.getDirection().yComponent());
    }

    private boolean validMove(int unitID, Direction direction) {
        Pair<Integer, Integer> newLocation = new Pair<>(
                this.units.get(unitID).xPosition() + direction.xComponent(),
                this.units.get(unitID).yPosition() + direction.yComponent());

        return newLocation.a <= this.xExtent
                && newLocation.a >= 0
                && newLocation.b <= this.yExtent
                && newLocation.b >= 0
                && !resourceLocations.contains(newLocation)
                && !isUnitAt(newLocation);
    }

    private boolean isUnitAt(Pair<Integer, Integer> location) {
        return this.footmanLocations.containsValue(location)
                || this.archerLocations.containsValue(location);
    }

    // I assume we can only attack directly N, E, S, or W since we can only move N, E, S, or W
    private boolean validAttack(int unitID, int enemyID) {
        return movesBetween(unitID, enemyID) <= 1;
    }

    private int movesBetween(int aID, int bID) {
        return Math.abs(this.units.get(aID).xPosition() - this.units.get(bID).xPosition())
                + Math.abs(this.units.get(aID).yPosition() - this.units.get(bID).yPosition());
    }
}
