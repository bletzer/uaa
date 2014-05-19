/*
 * ******************************************************************************
 *      Cloud Foundry 
 *      Copyright (c) [2009-2014] Pivotal Software, Inc. All Rights Reserved.
 *
 *      This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *      You may not use this product except in compliance with the License.
 *
 *      This product includes a number of subcomponents with
 *      separate copyright notices and license terms. Your use of these
 *      subcomponents is subject to the terms and conditions of the
 *      subcomponent's license, as noted in the LICENSE file.
 * ******************************************************************************
 */

package org.cloudfoundry.identity.uaa.authentication.manager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.util.List;

/**
 * Chained authentication manager that works of simple conditions 
 */
public class ChainedAuthenticationManager implements AuthenticationManager {
    public static final String IF_PREVIOUS_FALSE = "ifPreviousFalse";
    public static final String IF_PREVIOUS_TRUE = "ifPreviousTrue";
    
    protected final Log logger = LogFactory.getLog(getClass());

    private AuthenticationManagerConfiguration[] delegates;

    public ChainedAuthenticationManager() {
    }

    public AuthenticationManagerConfiguration[] getDelegates() {
        return delegates;
    }

    public void setDelegates(AuthenticationManagerConfiguration[] delegates) {
        this.delegates = delegates;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (authentication == null) {
            return authentication;
        }
        UsernamePasswordAuthenticationToken output = null;
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
            output = (UsernamePasswordAuthenticationToken) authentication;
        } else {
            output = new UsernamePasswordAuthenticationToken(authentication.getPrincipal(), authentication.getCredentials(),
                            authentication.getAuthorities());
            output.setAuthenticated(authentication.isAuthenticated());
            output.setDetails(authentication.getDetails());
        }
        boolean authenticated = false;
        Authentication auth = null;
        AuthenticationException lastException = null;
        boolean lastResult = false;
        for (int i=0; i<delegates.length; i++) {
                
                boolean shallAuthenticate = (i==0) || 
                    (lastResult && IF_PREVIOUS_TRUE.equals(delegates[i].getRequired())) ||
                    ((!lastResult) && IF_PREVIOUS_FALSE.equals(delegates[i].getRequired()));    
                    

                if (shallAuthenticate) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Attempting chained authentication of " + output + " with manager:" + delegates[i].getAuthenticationManager() + " required:" + delegates[i].getRequired());
                    }
                    Authentication thisAuth = null;
                    try {
                        thisAuth = delegates[i].getAuthenticationManager().authenticate(auth!=null ? auth : output);
                    } catch (AuthenticationException x) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Chained authentication exception:", x);
                        }
                        lastException = x;
                    }
                    lastResult = thisAuth != null && thisAuth.isAuthenticated();
                    
                    if (lastResult) {
                        authenticated = true;
                        auth = thisAuth;
                    }
                } else {
                    lastResult = false;
                }
            if (logger.isDebugEnabled()) {
                logger.debug("Chained Authentication status of "+output+ " with manager:"+delegates[i]+"; Authenticated:"+authenticated);
            }
        }
        if (authenticated) {
            return auth;
        } else if (lastException!=null) {
            //we had at least one authentication exception, throw it
            throw lastException;
        } else {
            //not authenticated, but return the last of the result
            return auth;
        }
    }
    
    public static class AuthenticationManagerConfiguration {
        private AuthenticationManager authenticationManager;
        private String required = null;

        public AuthenticationManagerConfiguration() {
        }

        public AuthenticationManagerConfiguration(AuthenticationManager authenticationManager, String required) {
            this.authenticationManager = authenticationManager;
            this.required = required;
        }

        public AuthenticationManager getAuthenticationManager() {
            return authenticationManager;
        }

        public void setAuthenticationManager(AuthenticationManager authenticationManager) {
            this.authenticationManager = authenticationManager;
        }

        public String getRequired() {
            return required;
        }

        public void setRequired(String required) {
            boolean valid = false;
            if (IF_PREVIOUS_FALSE.equals(required) ||
                IF_PREVIOUS_TRUE.equals(required)) {
                valid = true;
            }
            
            if (!valid) {
                throw new IllegalArgumentException(required+ " is not a valid value for property 'required'");
            }
            
            this.required = required;
        }
    }

}
