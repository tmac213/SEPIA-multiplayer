package edu.cwru.sepia.agent.minimax;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.util.Direction;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MinimaxAlphaBeta extends Agent {

    private final int numPlys;

    private static final int ATTACK_WEIGHT = 3;
    private static final int SOUTH_WEIGHT = 1;

    public MinimaxAlphaBeta(int playernum, String[] args) {
        super(playernum);

        if(args.length < 1) {
            System.err.println("You must specify the number of plys");
            System.exit(1);
        }

        numPlys = Integer.parseInt(args[0]);
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView newstate, History.HistoryView statehistory) {
        return middleStep(newstate, statehistory);
    }
    
    @Override
    public Map<Integer, Action> middleStep(State.StateView newstate, History.HistoryView statehistory) {
        GameStateChild bestChild = alphaBetaSearch(new GameStateChild(newstate),
                numPlys,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY);
        System.out.println(bestChild.action);
        return bestChild.action;
    }

    @Override
    public void terminalStep(State.StateView newstate, History.HistoryView statehistory) {

    }

    @Override
    public void savePlayerData(OutputStream os) {

    }

    @Override
    public void loadPlayerData(InputStream is) {

    }

    /**
     * You will implement this.
     *
     * This is the main entry point to the alpha beta search. Refer to the slides, assignment description
     * and book for more information.
     *
     * Try to keep the logic in this function as abstract as possible (i.e. move as much SEPIA specific
     * code into other functions and methods)
     *
     * @param node The action and state to search from
     * @param depth The remaining number of plys under this node
     * @param alpha The current best value for the maximizing node from this node to the root
     * @param beta The current best value for the minimizing node from this node to the root
     * @return The best child of this node with updated values
     */
    public GameStateChild alphaBetaSearch(GameStateChild node, int depth, double alpha, double beta) {

        if (depth == 0) {
            return node;
        }

        GameStateChild ret = null;

        if (numPlys % 2 == depth % 2) {
            double v = Double.NEGATIVE_INFINITY;
            for (GameStateChild child : orderChildrenWithHeuristics(node.state.getChildren(true))) {
                double childUtility = alphaBetaSearch(child, depth - 1, alpha, beta).state.getUtility();
                if (childUtility > v) {
                    v = childUtility;
                    ret = child;
                }
                if (v >= beta) {
                    break;
                } else {
                    alpha = Math.max(v, alpha);
                }
            }
        } else {
            double v = Double.POSITIVE_INFINITY;
            for (GameStateChild child : orderChildrenWithHeuristics(node.state.getChildren(false))) {
                double childUtility = alphaBetaSearch(child, depth - 1, alpha, beta).state.getUtility();
                if (childUtility < v) {
                    v = childUtility;
                    ret = child;
                }
                if (v <= alpha) {
                    break;
                } else {
                    beta = Math.min(v, beta);
                }
            }
        }

        return ret;
    }

    /**
     * You will implement this.
     *
     * Given a list of children you will order them according to heuristics you make up.
     * See the assignment description for suggestions on heuristics to use when sorting.
     *
     * Use this function inside of your alphaBetaSearch method.
     *
     * Include a good comment about what your heuristics are and why you chose them.
     *
     * @param children The original list of children.
     * @return The list of children sorted by your heuristic.
     */
    public List<GameStateChild> orderChildrenWithHeuristics(List<GameStateChild> children) {
        Collections.sort(children, this::compareGameStateChildren);
        return children;
    }

    // I chose the number of attacks and the number of South moves because I know that my utility function
    //   gives a lot of weight to those types of actions. Attacks will generally get you into a better state
    //   because the enemy HP will be lower, and South moves generally get you closer to the enemy because
    //   of the initial map layout.
    private int compareGameStateChildren(GameStateChild g1, GameStateChild g2) {
        int g1Attacks = 0;
        int g2Attacks = 0;
        int g1Souths = 0;
        int g2Souths = 0;


        for (Action action : g1.action.values()) {
            if (action.getType().equals(ActionType.PRIMITIVEATTACK)) {
                ++g1Attacks;
            } else {
                if (((DirectedAction) action).getDirection().equals(Direction.SOUTH)) {
                    ++g1Souths;
                }
            }
        }

        for (Action action : g2.action.values()) {
            if (action.getType().equals(ActionType.PRIMITIVEATTACK)) {
                ++g2Attacks;
            } else {
                if (((DirectedAction) action).getDirection().equals(Direction.SOUTH)) {
                    ++g2Souths;
                }
            }
        }

        return ((g1Attacks - g2Attacks) * ATTACK_WEIGHT) + ((g1Souths - g2Souths) * SOUTH_WEIGHT);
    }
}
