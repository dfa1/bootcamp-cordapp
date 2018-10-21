package java_examples;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.util.List;

// Implements ContractState (all states must implement ContractState or a sub-interface).
public class IAmAState implements ContractState {
    // The attributes that will be stored on the ledger as part of the state.
    private final String firstAttribute;
    private final int secondAttribute;
    private final Party firstParty;
    private final Party secondParty;

    // The constructor used to create an instance of the state.
    public IAmAState(String firstAttribute, int secondAttribute, Party firstParty, Party secondParty) {
        this.firstAttribute = firstAttribute;
        this.secondAttribute = secondAttribute;
        this.firstParty = firstParty;
        this.secondParty = secondParty;
    }

    // Overrides participants, the only field defined by ContractState.
    // Defines which parties will store the state.
    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(firstParty, secondParty);
    }

    // Getters for the state's attributes.
    public String getFirstAttribute() {
        return firstAttribute;
    }

    public int getSecondAttribute() {
        return secondAttribute;
    }

    public Party getFirstParty() {
        return firstParty;
    }

    public Party getSecondParty() {
        return secondParty;
    }
}