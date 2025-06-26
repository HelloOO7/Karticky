package cz.spojenka.android.util;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectionUtils {

    /**
     * Get the first element of a list, if it exists.
     *
     * @param list The list
     * @return The first element, or an empty Optional if the list is empty
     * @param <T> The type of the elements in the list
     */
    public static <T> Optional<T> getFirst(List<T> list) {
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * Get the last element of a list, if it exists.
     *
     * @param list The list
     * @return The last element, or an empty Optional if the list is empty
     * @param <T> The type of the elements in the list
     */
    public static <T> Optional<T> getLast(List<T> list) {
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1));
    }

    /**
     * Convert an array of ints to a list of Integers.
     *
     * @param arr The int[]
     * @return The List&lt;Integer&gt;
     */
    public static List<Integer> asList(int[] arr) {
        return Arrays.stream(arr).boxed().collect(Collectors.toList());
    }

    /**
     * Check if two sets are equal, that is, they contain the same elements, or are both empty.
     *
     * @param a The first set
     * @param b The second set
     * @return True if the sets are equal, false otherwise
     * @param <T> The type of the elements in the sets
     */
    public static <T> boolean setEquals(Set<T> a, Set<T> b) {
        return a.size() == b.size() && a.containsAll(b);
    }

    /**
     * Check if two sets intersect, that is, if they share at least one element.
     *
     * @param a The first set
     * @param b The second set
     * @return True if the sets intersect, false otherwise
     * @param <T> The type of the elements in the sets
     */
    public static <T> boolean setIntersects(Set<T> a, Set<T> b) {
        return a.stream().anyMatch(b::contains);
    }

    /**
     * Create a new set that is the union of two sets, containing all elements from both.
     *
     * @param a The first set
     * @param b The second set
     * @return A new set containing all elements from both sets
     * @param <T> The type of the elements in the sets
     */
    public static <T> Set<T> setUnion(Set<T> a, Set<T> b) {
        return Stream.concat(a.stream(), b.stream()).collect(Collectors.toSet());
    }

    /**
     * Convert a list of objects to a list of their string representations (using {@link String#valueOf(Object)}).
     *
     * @param list The list of objects
     * @return The list of strings
     */
    public static List<String> toStringList(List<?> list) {
        return list.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
    }
}
