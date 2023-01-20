package org.schambon.loadsimrunner.template;

import java.util.Collections;
import java.util.List;

public class RememberField {
    public String field;
    public boolean preload;
    public List<String> compound;
    public String name;
    public int number;

    public RememberField(String field, List<String> compound, String name, boolean preload, int number) {
        this.field = field;
        this.preload = preload;
        if (compound == null) {
            this.compound = Collections.emptyList();
        } else {
            this.compound = compound;
        }
        if (name == null) {
            this.name = field.replace('.', '_');
        } else {
            this.name = name;
        }
        this.number = number;
    }

    // compound trumps field, basically
    public boolean isSimple() {
        return compound.isEmpty();
    }
}