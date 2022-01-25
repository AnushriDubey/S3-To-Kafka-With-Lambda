package com.nodomain;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;

import java.util.ArrayList;
import java.util.List;

public class HttpApiIntegartionLambda implements RequestHandler<APIGatewayV2HTTPEvent, List<Integer>>{

    @Override
    public List<Integer> handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        context.getLogger().log(input.toString());
        ArrayList<Integer> al = new ArrayList<>();
        al.add(1);al.add(2);al.add(3);al.add(4);
        return al;
    }
    
}
