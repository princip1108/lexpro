package com.lexpro.lexprobackend.recommendation.client;

public interface RetrievalClient {

    RetrievalContract.NormalizeResponse normalize(RetrievalContract.NormalizeRequest request);

    RetrievalContract.RetrieveResponse retrieve(RetrievalContract.RetrieveRequest request);
}
