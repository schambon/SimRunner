package org.schambon.loadsimrunner;

import java.util.List;
import java.util.stream.Collectors;

public class ListGenerator implements Generator {

    private List<Generator> subgenerators;

    public ListGenerator(List<Generator> subgenerators) {
        this.subgenerators = subgenerators;
    }

    @Override
    public Object generate() {
        return subgenerators.stream().map(it -> it.generate()).collect(Collectors.toList());
    }

    public Object generate(int index) {
        return subgenerators.get(index).generate();
    }
    
    public int size() {
        return subgenerators.size();
    }
}
