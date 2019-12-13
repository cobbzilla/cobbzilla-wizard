package org.cobbzilla.wizard.validation;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor @AllArgsConstructor @ToString
public class MultiViolationException extends RuntimeException {

    @Getter @Setter private List<ConstraintViolationBean> violations = new ArrayList<>();

}
