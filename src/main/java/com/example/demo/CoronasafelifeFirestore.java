/*
 * Copyright 2021 CovidWarriors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.demo;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteResult;
import com.server.coronasafe.models.Data;
import com.server.coronasafe.models.ResourceData;
import com.server.coronasafe.models.ResourceQuery;
import com.server.coronasafe.models.User;

/**
 * A simple Quick start application demonstrating how to connect to Firestore
 * and add and query documents.
 */
public class CoronasafelifeFirestore {

	private Firestore db;

	/**
	 * Initialize Firestore using default project ID.
	 */
	public CoronasafelifeFirestore() {
		// [START fs_initialize]
		// [START firestore_setup_client_create]
//		Firestore db = FirestoreOptions.getDefaultInstance().getService();
		// [END firestore_setup_client_create]
		// [END fs_initialize]
//		this.db = db;
	}

	public CoronasafelifeFirestore(String projectId) throws Exception {	
		// [START fs_initialize_project_id]
		// [START firestore_setup_client_create_with_project_id]
		FirestoreOptions firestoreOptions =
				FirestoreOptions.getDefaultInstance().toBuilder()
				.setProjectId(projectId)
				.setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(System.getenv("FIREBASE_JSON").getBytes())))
				.build();
		Firestore db = firestoreOptions.getService();
		// [END firestore_setup_client_create_with_project_id]
		// [END fs_initialize_project_id]
		this.db = db;
	}

	Firestore getDb() {
		return db;
	}


	void runQuery() throws Exception {
		// [START fs_add_query]
		// asynchronously query for all users born before 1900
		ApiFuture<QuerySnapshot> query =
				db.collection("users").whereLessThan("born", 1900).get();
		// ...
		// query.get() blocks on response
		QuerySnapshot querySnapshot = query.get();
		List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
		for (QueryDocumentSnapshot document : documents) {
			System.out.println("User: " + document.getId());
			System.out.println("First: " + document.getString("first"));
			if (document.contains("middle")) {
				System.out.println("Middle: " + document.getString("middle"));
			}
			System.out.println("Last: " + document.getString("last"));
			System.out.println("Born: " + document.getLong("born"));
		}
		// [END fs_add_query]
	}

	void addData( Data details, String resource) throws Exception {

		DocumentReference docRef = db.collection("data").document(resource);
		ApiFuture<WriteResult> result = docRef.set(details);

		System.out.println(result.get().getUpdateTime().toString());
	}


	/** Closes the gRPC channels associated with this instance and frees up their resources. */
	void close() throws Exception {
		db.close();
	}

	public List<User> getAllUsers() throws InterruptedException, ExecutionException {
		ApiFuture<QuerySnapshot> query = db.collection("Users").get();
		QuerySnapshot querySnapshot = query.get();
		List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
		List<User> usersList = new ArrayList<User>();
		for (QueryDocumentSnapshot document : documents) {
			System.out.println("User: " + document.getId());
			System.out.println("Token: " + document.getString("token"));
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

			User user = mapper.convertValue(document.getData(), User.class);
			List<ResourceQuery> resourceQueries = new ArrayList<ResourceQuery>();

			QuerySnapshot resourceQuerySnapshot  = document.getReference().collection("queries").get().get();
			for(QueryDocumentSnapshot resourceQueryDocument: resourceQuerySnapshot.getDocuments()) {
				ResourceQuery resourceQuery = mapper.convertValue(resourceQueryDocument.getData(), ResourceQuery.class);
				resourceQueries.add(resourceQuery);
			}
			user.setQueries(resourceQueries);
			usersList.add(user);
		}

		return usersList;

	}

	public Data getData(String resource) throws InterruptedException, ExecutionException {
		ApiFuture<DocumentSnapshot> query = db.collection("data").document(resource).get();
		DocumentSnapshot querySnapshot = query.get();
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		Data data = mapper.convertValue(querySnapshot.getData(), Data.class);
		return data;
	}

	public void addFoodData(Data details, String resource) throws InterruptedException, ExecutionException {

		Map<String, Map<String, List<ResourceData>>> map = details.getData().stream()
				.collect(Collectors.groupingBy(ResourceData::getState,
						Collectors.groupingBy(ResourceData::getDistrict)));

		map.forEach((state, districtMap) -> {
			DocumentReference docRef = db.collection("data").document(resource).collection(state).document("districts");
			ApiFuture<WriteResult> result = docRef.set(districtMap,SetOptions.merge());
			try {
				System.out.println("Data initialised "+resource+" for "+state+" - "+result.get().getUpdateTime().toString());
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		});

	}

	public Data getFoodData(String resource)  throws InterruptedException, ExecutionException {
		ApiFuture<DocumentSnapshot> query = db.collection("data").document(resource).get();
		DocumentSnapshot querySnapshot = query.get();
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		Data data = mapper.convertValue(querySnapshot.getData(), Data.class);
		return data;
	}

	public void addLastCheckedCommit(String lastCommit) {
		DocumentReference docRef = db.collection("compare").document("commit");
		Map<String, Object> docData = new HashMap<>();
		docData.put("lastcommit", lastCommit);
		ApiFuture<WriteResult> result = docRef.set(docData,SetOptions.merge());
	}

	public String getLastCheckedCommit() throws InterruptedException, ExecutionException {
		ApiFuture<DocumentSnapshot> query = db.collection("compare").document("commit").get();
		DocumentSnapshot querySnapshot = query.get();
		return (String) querySnapshot.getData().get("lastcommit");
	}

	public Object getEtag() throws InterruptedException, ExecutionException  {
		ApiFuture<DocumentSnapshot> query = db.collection("compare").document("etags").get();
		DocumentSnapshot querySnapshot = query.get();
		return querySnapshot.getData().get("etag");
	}
	
	public void addetag(String eTag) {
		DocumentReference docRef = db.collection("compare").document("etags");
		Map<String, Object> docData = new HashMap<>();
		docData.put("etag", eTag);
		ApiFuture<WriteResult> result = docRef.set(docData,SetOptions.merge());
	}

	public void addWebhookData(String url) {
		DocumentReference docRef = db.collection("webhooks").document();
		Map<String, Object> docData = new HashMap<>();
		docData.put("url", url);
		docRef.set(docData,SetOptions.merge());
	}
	
	public List<String> getAllWebhookData() throws InterruptedException, ExecutionException  {
		ApiFuture<QuerySnapshot> query = db.collection("webhooks").get();
		QuerySnapshot querySnapshot = query.get();
		List<String> webhookUrlList = new ArrayList<String>();
		
		List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
		for (QueryDocumentSnapshot document : documents) {
			webhookUrlList.add(document.getString("url"));
		}
		return webhookUrlList;
	}
}
