package com.indemnify.contract.manager.service;

import com.hedera.hashgraph.sdk.*;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.indemnify.contract.manager.model.ContractCall;
import com.indemnify.contract.manager.util.EnvUtils;
import com.indemnify.contract.manager.util.HederaClient;
import com.indemnify.contract.manager.util.Utils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

import static com.indemnify.contract.manager.util.Utils.FILE_PART_SIZE;

@Service
public class ContractService {

    Client client = HederaClient.getHederaClientInstance();


    public String createContract(String byteCode) throws HederaPreCheckStatusException, HederaReceiptStatusException, TimeoutException {
        FileId bytecodeFile = createBytecodeFile(byteCode);
        var contractTxId =
                new ContractCreateTransaction()
                        .setBytecodeFileId(bytecodeFile)
                        .setAutoRenewPeriod(Duration.ofSeconds(8000000))
                        .setGas(100_000_000)
                        .setMaxTransactionFee(new Hbar(20))
                        .setAdminKey(EnvUtils.getOperatorKey()) //allows to delete contract
                        .execute(client);
        var contractReceipt = contractTxId.getReceipt(client);
        var contractId = contractReceipt.contractId;
        return contractId.toString();



    }

    //delete contract
    public boolean deleteContract(String contractId) throws HederaPreCheckStatusException, TimeoutException, HederaReceiptStatusException {
        new ContractDeleteTransaction()
                .setContractId(ContractId.fromString(contractId))
                .setTransferAccountId(EnvUtils.getOperatorId())
                .execute(client)
                .getReceipt(client); //to confirm deletion
        System.out.println("Deleted contract: " + contractId);
        return true;
    }

    //get info of a contract
    //FIXME: https://github.com/hashgraph/hedera-sdk-java/issues/404
    public ContractInfo getContractInfo(String contractId) throws HederaPreCheckStatusException, TimeoutException {
        Hbar cost = new ContractInfoQuery()
                .setContractId(ContractId.fromString(contractId))
                .getCost(client);
        ContractInfo info = new ContractInfoQuery()
                .setContractId(ContractId.fromString(contractId))
                .setQueryPayment(cost)
                .execute(client);
        return info;
    }

    //get bytecode of a contract
    public String queryBytecodeContract(String contractId) throws HederaPreCheckStatusException, TimeoutException {

        Hbar cost = new ContractByteCodeQuery()
                .setContractId(ContractId.fromString(contractId))
                .getCost(client);
        var contents = new ContractByteCodeQuery()
                .setContractId(ContractId.fromString(contractId))
                .setQueryPayment(Utils.addMargin(cost, 50))
                .execute(client);
        System.out.println("Bytecode for contact: " + contents.toStringUtf8());
        return contents.toStringUtf8();

    }

    //get state size stored on the contract
    public long queryContractStatesize(String contractId) throws HederaPreCheckStatusException, TimeoutException {
        ContractInfo info = getContractInfo(contractId);
        return info.storage;
    }

    public boolean executeTransactionOnContract(ContractCall request) throws HederaPreCheckStatusException, TimeoutException, HederaReceiptStatusException {
        ContractId contractId = ContractId.fromString(request.getContractId());
        String functionName = request.getFunctionName();
        String argument = request.getArgument();

        var contractExecTxnId = new ContractExecuteTransaction()
                .setContractId(contractId)
                .setGas(100_000_000)
                .setFunction(functionName, new ContractFunctionParameters()
                        .addString(argument))
                .execute(client);
        // if this doesn't throw then we know the contract executed successfully
        contractExecTxnId.getReceipt(client);
        return true;
    }

    public String contractCallQuery(ContractCall request) throws HederaPreCheckStatusException, TimeoutException {
        ContractId contractId = ContractId.fromString(request.getContractId());
        String functionName = request.getFunctionName();
        String argument = request.getArgument();

        Hbar cost = new ContractCallQuery()
                .setContractId(contractId)
                .setGas(100_000_000) //get this value from remix + trial and error on hedera.
                .setFunction(
                        functionName,
                        new ContractFunctionParameters()
                                .addString(argument))
                .getCost(client);
        long costLong = cost.toTinybars();
        long estimatedCost = costLong + costLong / 50; // add 2% of this cost
        var contractCallResult = new ContractCallQuery()
                .setContractId(contractId)
                //.setQueryPayment(Hbar.from(estimatedCost))
                .setGas(100_000_000) //get this value from remix + trial and error on hedera.
                .setFunction(
                        functionName,
                        new ContractFunctionParameters()
                                .addString(argument))
                .execute(client);

        if (contractCallResult.errorMessage != null) {
            String msg = "error calling contract: " + contractCallResult.errorMessage;
            throw new RuntimeException(msg);
        }
        //get value from EVM:
        return contractCallResult.getString(0);
    }


    private FileId createBytecodeFile(String byteCodeHex) throws HederaPreCheckStatusException, TimeoutException, HederaReceiptStatusException {
        byte[] byteCode = byteCodeHex.getBytes();

        int numParts = byteCode.length / FILE_PART_SIZE;
        int remainder = byteCode.length % FILE_PART_SIZE;
        // add in 5k chunks
        byte[] firstPartBytes;
        if (byteCode.length <= FILE_PART_SIZE) {
            firstPartBytes = byteCode;
            remainder = 0;
        } else {
            firstPartBytes = Utils.copyBytes(0, FILE_PART_SIZE, byteCode);
        }

        // create the contract's bytecode file
        var fileTxId =
                new FileCreateTransaction()
                        .setExpirationTime(Instant.now().plus(Duration.ofMillis(7890000000L)))
                        // Use the same key as the operator to "own" this file
                        .setKeys(EnvUtils.getOperatorKey())
                        .setContents(firstPartBytes)
                        .setMaxTransactionFee(new Hbar(5))
                        .execute(client);

        var fileReceipt = fileTxId.getReceipt(client);
        FileId newFileId = fileReceipt.fileId;

        System.out.println("Bytecode file ID: " + newFileId);

        // add remaining chunks
        // append the rest of the parts
        for (int i = 1; i < numParts; i++) {
            byte[] partBytes = Utils.copyBytes(i * FILE_PART_SIZE, FILE_PART_SIZE, byteCode);
            new FileAppendTransaction()
                    .setFileId(newFileId)
                    .setMaxTransactionFee(new Hbar(5))
                    .setContents(partBytes)
                    .execute(client);
        }
        // appending remaining data
        if (remainder > 0) {
            byte[] partBytes = Utils.copyBytes(numParts * FILE_PART_SIZE, remainder, byteCode);
            new FileAppendTransaction()
                    .setFileId(newFileId)
                    .setMaxTransactionFee(new Hbar(5))
                    .setContents(partBytes)
                    .execute(client);
        }

        return newFileId;
    }
}
