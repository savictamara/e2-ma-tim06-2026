package rs.ac.uns.ftn.slagalica.domain.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyNumberService {
    public boolean usesOnlyAvailableNumbers(String expression, List<Integer> available) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Integer number : available) {
            counts.put(number, counts.getOrDefault(number, 0) + 1);
        }
        int i = 0;
        while (i < expression.length()) {
            char c = expression.charAt(i);
            if (Character.isDigit(c)) {
                int start = i;
                while (i < expression.length() && Character.isDigit(expression.charAt(i))) {
                    i++;
                }
                int value = Integer.parseInt(expression.substring(start, i));
                int count = counts.getOrDefault(value, 0);
                if (count <= 0) {
                    return false;
                }
                counts.put(value, count - 1);
            } else {
                i++;
            }
        }
        return true;
    }
}
