/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.action.role;

import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.Supplier;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.security.authz.RoleDescriptor;
import org.elasticsearch.xpack.security.authz.permission.KibanaRole;
import org.elasticsearch.xpack.security.authz.permission.LogstashSystemRole;
import org.elasticsearch.xpack.security.authz.store.NativeRolesStore;
import org.elasticsearch.xpack.security.authz.store.ReservedRolesStore;

import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.common.Strings.arrayToDelimitedString;

public class TransportGetRolesAction extends HandledTransportAction<GetRolesRequest, GetRolesResponse> {

    private final NativeRolesStore nativeRolesStore;
    private final ReservedRolesStore reservedRolesStore;

    @Inject
    public TransportGetRolesAction(Settings settings, ThreadPool threadPool, ActionFilters actionFilters,
                                   IndexNameExpressionResolver indexNameExpressionResolver,
                                   NativeRolesStore nativeRolesStore, TransportService transportService,
                                   ReservedRolesStore reservedRolesStore) {
        super(settings, GetRolesAction.NAME, threadPool, transportService, actionFilters, indexNameExpressionResolver,
                GetRolesRequest::new);
        this.nativeRolesStore = nativeRolesStore;
        this.reservedRolesStore = reservedRolesStore;
    }

    @Override
    protected void doExecute(final GetRolesRequest request, final ActionListener<GetRolesResponse> listener) {
        final String[] requestedRoles = request.names();
        final boolean specificRolesRequested = requestedRoles != null && requestedRoles.length > 0;
        final List<String> rolesToSearchFor = new ArrayList<>();
        final List<RoleDescriptor> roles = new ArrayList<>();

        if (specificRolesRequested) {
            for (String role : requestedRoles) {
                if (ReservedRolesStore.isReserved(role)) {
                    RoleDescriptor rd = reservedRolesStore.roleDescriptor(role);
                    assert rd != null  : "No descriptor for role " + role;
                    roles.add(rd);
                } else {
                    rolesToSearchFor.add(role);
                }
            }
        } else {
            roles.addAll(reservedRolesStore.roleDescriptors());
        }

        if (specificRolesRequested && rolesToSearchFor.isEmpty()) {
            // specific roles were requested but they were built in only, no need to hit the store
            listener.onResponse(new GetRolesResponse(roles.toArray(new RoleDescriptor[roles.size()])));
        } else {
            String[] roleNames = rolesToSearchFor.toArray(new String[rolesToSearchFor.size()]);
            nativeRolesStore.getRoleDescriptors(roleNames, ActionListener.wrap((foundRoles) -> {
                        roles.addAll(foundRoles);
                        listener.onResponse(new GetRolesResponse(roles.toArray(new RoleDescriptor[roles.size()])));
                    }, listener::onFailure));
        }
    }
}
