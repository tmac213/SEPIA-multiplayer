package edu.cwru.sepia.agent.minimax;

import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Pair;

/**
 * Created by Emilio on 3/2/16.
 */
public class GameUnit {
    String name;
    int id;
    int hp;
    int attackDamage;
    Pair<Integer, Integer> location;

    GameUnit(Unit unit) {
        this.name = unit.getTemplate().getName();
        this.id = unit.ID;
        this.hp = unit.getCurrentHealth();
        this.attackDamage = unit.getTemplate().getBasicAttack();
        this.location = new Pair<>(unit.getxPosition(), unit.getyPosition());
    }

    GameUnit(GameUnit old) {
        this.name = old.name;
        this.id = old.id;
        this.hp = old.hp;
        this.attackDamage = old.attackDamage;
        this.location = new Pair<>(old.location.a, old.location.b);
    }

    int xPosition() {
        return this.location.a;
    }

    int yPosition() {
        return this.location.b;
    }
}
