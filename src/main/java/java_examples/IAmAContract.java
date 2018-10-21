package java_examples;

import net.corda.core.contracts.*;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;

public class IAmAContract implements Contract {
    // Used to reference the contract in transactions.
    public static final String CONTRACT_ID = "java_examples.IAmAContract";

    public interface Commands extends CommandData {
        // A command that is only used to parametrise contract verification.
        class ACommand implements Commands {
        }

        // A command that also contains data to be used during contract verification.
        class AnotherCommand implements Commands {
            private final String data;

            public AnotherCommand(String data) {
                this.data = data;
            }

            public String getData() {
                return data;
            }
        }
    }

    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
        // *************************************************
        // Step 1 - Checking the "shape" of the transaction:
        // *************************************************
        if (tx.getInputStates().size() != 3) throw new IllegalArgumentException("There are three input states.");
        if (tx.getOutputStates().size() != 4) throw new IllegalArgumentException("There are four output states.");
        if (tx.getCommands().size() != 5) throw new IllegalArgumentException("There are five commands.");

        if (tx.inputsOfType(IAmAState.class).size() != 1) throw new IllegalArgumentException("There is one IAmAState input states.");
        if (tx.outputsOfType(IAmAState.class).size() != 2) throw new IllegalArgumentException("There are two IAmAState output states.");
        if (tx.commandsOfType(Commands.class).size() != 3) throw new IllegalArgumentException("There are three IAmAContract.Commands commands.");

        // And so on...

        // *********************************************
        // Step 2 - Grabbing the transaction's contents:
        // *********************************************

        // INPUTS
        // Grabbing all input states.
        final List<ContractState> inputStates = tx.getInputStates();
        // Grabbing all input states of type IAmAState.
        final List<IAmAState> iAmAStateInputs = tx.inputsOfType(IAmAState.class);
        // Grabbing all input states of type IAmAState that meet a criterion.
        final List<IAmAState> filteredIAmAStateInputs = tx.filterInputs(IAmAState.class, state -> state.getFirstAttribute().equals("state data"));
        // Grabbing the single input state of type IAmAState that meets a criterion.
        final IAmAState iAmAStateInput = tx.findInput(IAmAState.class, state -> state.getFirstAttribute().equals("state data"));

        // OUTPUTS
        // Grabbing all output states.
        final List<ContractState> outputStates = tx.getOutputStates();
        // Grabbing all output states of type IAmAState.
        final List<IAmAState> iAmAStateOutputs = tx.outputsOfType(IAmAState.class);
        // Grabbing all output states of type IAmAState that meet a criterion.
        final List<IAmAState> filteredIAmAStateOutputs = tx.filterOutputs(IAmAState.class, state -> state.getFirstAttribute().equals("state data"));
        // Grabbing the single output state of type IAmAState that meets a criterion.
        final IAmAState iAmAStateOutput = tx.findOutput(IAmAState.class, state -> state.getFirstAttribute().equals("state data"));

        // COMMANDS
        // Grabbing all commands.
        final List<CommandWithParties<CommandData>> commands = tx.getCommands();
        // Grabbing all commands associated with this contract.
        final List<Command<Commands>> iAmAContractCommands = tx.commandsOfType(Commands.class);
        // Grabbing a single command of type ACommand.
        final CommandWithParties<Commands.ACommand> aCommand = requireSingleCommand(tx.getCommands(), Commands.ACommand.class);
        // Grabbing all the commands of type AnotherCommand that meet a criterion.
        final List<Command<Commands.AnotherCommand>> filteredCommandsWithData = tx.filterCommands(Commands.AnotherCommand.class, command -> command.getData().equals("expected command contents"));
        // Grabbing the single command of type AnotherCommand that meets a criterion.
        final Command<Commands.AnotherCommand> commandWithData = tx.findCommand(Commands.AnotherCommand.class, command -> command.getData().equals("expected command contents"));
        // Each command pairs a list of signers with a value. Here, we grab the command's value.
        final Commands.AnotherCommand commandWithDataValue = commandWithData.getValue();

        // *********************************************
        // Step 3 - Checking the transaction's contents:
        // *********************************************
        if (iAmAStateInput.getFirstAttribute().length() < 10)
            throw new IllegalArgumentException("The input and output IAmAState must have the same data.");
        if (!(iAmAStateInput.getFirstAttribute().equals(iAmAStateOutput.getFirstAttribute())))
            throw new IllegalArgumentException("The input and output IAmAState must have the same data.");
        if (!(commandWithDataValue.getData().equals(iAmAStateOutput.getFirstAttribute())))
            throw new IllegalArgumentException("The AnotherCommand's data must match the output IAmAState's data.");

        // And so on...

        // ***********************************************
        // Step 4 - Checking the transaction's signatures:
        // ***********************************************
        // Extracting the list of required signers from a command.
        final List<PublicKey> requiredSigners = commandWithData.getSigners();
        // Checking that the input IAmAState's first party is a required signer.
        if (!(requiredSigners.contains(iAmAStateInput.getFirstParty().getOwningKey())))
            throw new IllegalArgumentException("The input IAmAState's party is a required signer");
        // Checking that the output IAmAState's first party is a required signer.
        if (!(requiredSigners.contains(iAmAStateOutput.getFirstParty().getOwningKey())))
            throw new IllegalArgumentException("The output IAmAState's party is a required signer");

        // And so on...
    }
}