package io.galeb.entity;

import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.OneToMany;

@Entity
public class RuleType extends AbstractEntity<RuleType> {

    private static final long serialVersionUID = 5596582746795373010L;

    @OneToMany
    private Set<Rule> rules;

    public RuleType(String name) {
        setName(name);
    }

    protected RuleType() {
        //
    }

    public Set<Rule> getRules() {
        return rules;
    }
}
