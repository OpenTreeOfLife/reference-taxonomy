/**
   A binary relation with both indexes.
*/

package org.opentreeoflife.smasher;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class Correspondence<Sheep, Goat> {

    Map<Sheep, List<Goat>> sheepToGoats = new HashMap<Sheep, List<Goat>>();
    Map<Goat, List<Sheep>> goatToSheep = new HashMap<Goat, List<Sheep>>();

    public void add(Sheep s, Goat g) {
        List<Goat> gs = sheepToGoats.get(s);
        if (gs == null) {
            gs = new ArrayList<Goat>();
            sheepToGoats.put(s, gs);
        }
        gs.add(g);
        List<Sheep> ss = goatToSheep.get(g);
        if (ss == null) {
            ss = new ArrayList<Sheep>();
            goatToSheep.put(g, ss);
        }
        ss.add(s);
    }

    public Iterable sheep() {
        return sheepToGoats.keySet();
    }

    public Iterable goats() {
        return goatToSheep.keySet();
    }

    public void put(Sheep s, List<Goat> gs) {
        // sheepToGoats.put(s, gs);
        for (Goat g : gs)
            add(s, g);
    }

    public List<Goat> get(Sheep s) {
        return sheepToGoats.get(s);
    }

    public List<Sheep> coget(Goat g) {
        return goatToSheep.get(g);
    }

    public int size() { return sheepToGoats.size(); }
    public int cosize() { return goatToSheep.size(); }

}
