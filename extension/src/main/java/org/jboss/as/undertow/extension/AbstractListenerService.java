/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.undertow.extension;

import java.io.IOException;
import java.net.InetSocketAddress;

import io.undertow.server.OpenListener;
import org.jboss.as.network.ManagedBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * @author Tomaz Cerar
 */
public abstract class AbstractListenerService<T> implements Service<T> {

    private final String name;
    protected final InjectedValue<XnioWorker> worker = new InjectedValue<>();
    protected final InjectedValue<SocketBinding> binding = new InjectedValue<>();
    protected final InjectedValue<Pool> bufferPool = new InjectedValue<>();
    protected final InjectedValue<ServerService> serverService = new InjectedValue<>();

    protected volatile OpenListener openListener;
    private volatile ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener;

    OptionMap serverOptions = OptionMap.builder()
            .set(Options.WORKER_ACCEPT_THREADS, Runtime.getRuntime().availableProcessors())
            .set(Options.TCP_NODELAY, true)
            .set(Options.REUSE_ADDRESSES, true)
            .getMap();


    protected AbstractListenerService(String name) {
        this.name = name;
    }

    public InjectedValue<XnioWorker> getWorker() {
        return worker;
    }

    public InjectedValue<SocketBinding> getBinding() {
        return binding;
    }

    public InjectedValue<Pool> getBufferPool() {
        return bufferPool;
    }

    public InjectedValue<ServerService> getServerService() {
        return serverService;
    }

    protected int getBufferSize() {
        return 1024;
    }

    public String getName() {
        return name;
    }

    public abstract boolean isSecure();

    protected void registerBinding() {
        binding.getValue().getSocketBindings().getNamedRegistry().registerBinding(new ListenerBinding(binding.getValue()));
    }

    protected void unRegisterBinding() {
        final SocketBinding binding = this.binding.getValue();
        binding.getSocketBindings().getNamedRegistry().unregisterBinding(binding.getName());
    }

    @Override
    public void start(StartContext context) throws StartException {
        serverService.getValue().registerListener(this);
        try {
            final InetSocketAddress socketAddress = binding.getValue().getSocketAddress();
            openListener = createOpenListener();
            acceptListener = ChannelListeners.openListenerAdapter(openListener);


            openListener.setRootHandler(serverService.getValue().getRoot());
            startListening(worker.getValue(), socketAddress, acceptListener);
            registerBinding();
        } catch (IOException e) {
            throw new StartException("Could not start http listener", e);
        }
    }

    @Override
    public void stop(StopContext context) {
        serverService.getValue().unRegisterListener(this);
        stopListening();
        unRegisterBinding();
    }

    protected abstract OpenListener createOpenListener();

    abstract void startListening(XnioWorker worker, InetSocketAddress socketAddress, ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener) throws IOException;

    abstract void stopListening();


    private static class ListenerBinding implements ManagedBinding {

        private final SocketBinding binding;

        private ListenerBinding(final SocketBinding binding) {
            this.binding = binding;
        }

        @Override
        public String getSocketBindingName() {
            return binding.getName();
        }

        @Override
        public InetSocketAddress getBindAddress() {
            return binding.getSocketAddress();
        }

        @Override
        public void close() throws IOException {

        }
    }
}
