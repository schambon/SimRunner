package org.schambon.loadsimrunner;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RemindingGenerator implements Generator {

    private List<Object> rememberedValues;
    
    public RemindingGenerator(List<Object> rememberedValues) {
        this.rememberedValues = rememberedValues;
    }

    @Override
    public Object generate() {
        if (rememberedValues.size() == 0) return null;
        
        int itemNum = ThreadLocalRandom.current().nextInt(rememberedValues.size());
        return rememberedValues.get(itemNum);
    }
    
}
