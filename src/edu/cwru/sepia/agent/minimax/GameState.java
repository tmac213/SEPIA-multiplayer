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
    private static final int WEIGHT_ARCHER_HP = -2;
    private static final int WEIGHT_FOOTMAN_ALIVE = 10;
    private static final int WEIGHT_ARCHER_ALIVE = -20;
//    int WEIGHT_FOOTMAN_HP = 0;
//    int WEIGHT_ARCHER_HP = 0;
//    int WEIGHT_FOOTMAN_ALIVE = 0;
//    int WEIGHT_ARCHER_ALIVE = 0;
    private static final int WEIGHT_FOOTMAN_ARCHER_DISTANCE = -5;

    private static final Direction[] VALID_DIRECTIONS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private Map<Integer, GameUnit> units;
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
        try {
            State s = state.getStateCreator().createState();
            this.initialize(s);
        } catch (IOException e) {
            System.err.println("could not create state");
            e.printStackTrace();
            System.exit(1);
        }

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

    private void initialize(State state) {

        this.units = new HashMap<>();
        this.footmen = new HashSet<>();
        this.archers = new HashSet<>();
        this.footmanLocations = new HashMap<>();
        this.archerLocations = new HashMap<>();

        for (Unit unit : state.getUnits().values()) {
            switch (unit.getTemplate().getName()) {
                case "Footman":
                    GameUnit newFootman = new GameUnit(unit);
                    this.units.put(newFootman.id, newFootman);
                    this.footmen.add(newFootman);
                    this.footmanLocations.put(newFootman.id, newFootman.location);
                    System.out.print("added footman ");
                    break;
                case "Archer":
                    GameUnit newArcher = new GameUnit(unit);
                    this.units.put(newArcher.id, newArcher);
                    this.archers.add(newArcher);
                    this.archerLocations.put(newArcher.id, newArcher.location);
                    System.out.print("added archer ");
                    break;
                default:
                    System.out.print("unknown unit ");
                    break;
            }
            System.out.println(unit.toString());
        }

        this.resourceLocations = state.getResources().stream()
                .map(resource -> new Pair<>(resource.getxPosition(), resource.getyPosition()))
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
/*
        if (footmanLocations.containsKey(unit.id)) {
            footmanLocations.put(unit.id, unit.location);
        } else if (archerLocations.containsKey(unit.id)) {
            archerLocations.put(unit.id, unit.location);
        }*/
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
                .mapToDouble(this::nearestArcherDistance)
                .sum() / ((footmenAlive > 0 && archersAlive > 0) ? footmenAlive : 1);

        return WEIGHT_FOOTMAN_HP * footmenHP +
                WEIGHT_ARCHER_HP * archersHP +
                WEIGHT_FOOTMAN_ALIVE * footmenAlive +
                WEIGHT_ARCHER_ALIVE * archersAlive +
                WEIGHT_FOOTMAN_ARCHER_DISTANCE * averageFootmanToArcherDistance;
    }

    private double nearestArcherDistance(GameUnit footman) {
        double distance = 0;

        if (footman.hp <= 0) {
            return distance;
        }

        for (Pair<Integer, Integer> archerLocation : archerLocations.values()) {
            double temp = DistanceMetrics.euclideanDistance(footman.xPosition(), footman.yPosition(), archerLocation.a, archerLocation.b);
            distance = (distance == 0 || temp < distance) ? temp : distance;
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

        Map<Integer, List<Action>> validActions = validActions(footmanLocations.keySet(), archerLocations.keySet());

        ret.addAll(getActionCombinations(validActions).stream()
                .filter(validActionMap -> !actionsConflict(validActionMap))
                .map(actionCombination -> new GameStateChild(actionCombination, new GameState(this, actionCombination)))
                .collect(Collectors.toList()));

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

    private boolean validAttack(int unitID, int enemyID) {
        return movesBetween(unitID, enemyID) <= 1;
    }

    private int movesBetween(int aID, int bID) {
        return Math.abs(this.units.get(aID).xPosition() - this.units.get(bID).xPosition())
                + Math.abs(this.units.get(aID).yPosition() - this.units.get(bID).yPosition());
    }
}
