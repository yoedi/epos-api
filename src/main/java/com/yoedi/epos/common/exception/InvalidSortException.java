package com.yoedi.epos.common.exception;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code sort} names a property a list endpoint cannot order by. Mapped to 400 VALIDATION_ERROR.
 * Each list endpoint passes its own whitelist: an unknown property would otherwise fail inside the
 * query as a 500, and a hidden field (such as cost_price for a CASHIER) would still leak through ordering.
 */
public class InvalidSortException extends BusinessException {

    public InvalidSortException(String property) {
        super(ErrorCode.VALIDATION_ERROR, "Validasi gagal", "Pengurutan berdasarkan " + property + " tidak didukung");
    }

    /** Sort names whose Java property is not simply the camelCase form of the snake_case name. */
    private static final Map<String, String> SPECIAL_NAMES = Map.of("is_active", "active");
    private static final Pattern SNAKE_SEGMENT = Pattern.compile("_([a-z])");

    /**
     * Validates the requested sort against {@code allowed} (snake_case names, as in the JSON) and returns a
     * {@link Pageable} whose sort uses the entity's Java property names, ready to hand to a repository.
     * Spring Data reads {@code ?sort=created_at,desc} as the raw property "created_at"; Jackson's snake_case
     * setting does not apply to query parameters, so without this translation the query would fail as a 500.
     */
    public static Pageable translate(Pageable pageable, Set<String> allowed) {
        List<Sort.Order> orders = pageable.getSort().stream()
                .map(order -> {
                    if (!allowed.contains(order.getProperty())) {
                        throw new InvalidSortException(order.getProperty());
                    }
                    return order.withProperty(toJavaProperty(order.getProperty()));
                })
                .toList();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    /** created_at becomes createdAt; a dotted path such as category.name has no underscore and stays as is. */
    private static String toJavaProperty(String name) {
        String special = SPECIAL_NAMES.get(name);
        if (special != null) {
            return special;
        }
        return SNAKE_SEGMENT.matcher(name).replaceAll(m -> m.group(1).toUpperCase());
    }

    /** Throws if {@code sort} contains a property that is not in {@code allowed} (Java property names). */
    public static void check(Sort sort, Set<String> allowed) {
        sort.forEach(order -> {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty());
            }
        });
    }
}
