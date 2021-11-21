package com.indemnify.contract.manager;

import com.hedera.hashgraph.sdk.*;
import com.indemnify.contract.manager.model.ContractCall;
import com.indemnify.contract.manager.service.ContractByteCode;
import com.indemnify.contract.manager.service.ContractService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

@SpringBootApplication
public class IndemnifyContractManagerApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(IndemnifyContractManagerApplication.class, args);
        ContractService contractService = applicationContext.getBean(ContractService.class);
        try {
            String contractId = contractService.createContract(ContractByteCode.VENDING_MACHINE);
            System.out.println("contract Created = " + contractId);


            ContractInfo contractInfo = contractService.getContractInfo(contractId);
            System.out.println(contractInfo.toString());
            Map<TokenId, TokenRelationship> tokenRelationships = contractInfo.tokenRelationships;


            ContractCall contractCall = new ContractCall(contractId,"refill","1000");
            String contractCallQueryResponse = contractService.contractCallQuery(contractCall);
            System.out.println("contractCallQueryResponse = " + contractCallQueryResponse);


        } catch (HederaPreCheckStatusException e) {
            e.printStackTrace();
        } catch (HederaReceiptStatusException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }


    }

}
