/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.mobicents.protocols.ss7.isup.impl.message.parameter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.mobicents.protocols.ss7.isup.ParameterRangeInvalidException;
import org.mobicents.protocols.ss7.isup.message.parameter.ForwardGVNS;
import org.mobicents.protocols.ss7.isup.message.parameter.GVNSUserGroup;
import org.mobicents.protocols.ss7.isup.message.parameter.OriginatingParticipatingServiceProvider;
import org.mobicents.protocols.ss7.isup.message.parameter.TerminatingNetworkRoutingNumber;

/**
 * Start time:13:39:30 2009-04-04<br>
 * Project: mobicents-isup-stack<br>
 * 
 * @author <a href="mailto:baranowb@gmail.com"> Bartosz Baranowski
 *         </a>
 */
public class ForwardGVNSImpl extends AbstractParameter implements ForwardGVNS{
	
	
	//FIXME: we must add in numbers below max digits check - in case of max octets - only odd digits number is valid
	private OriginatingParticipatingServiceProviderImpl opServiceProvider = null;
	private GVNSUserGroupImpl gvnsUserGroup = null;
	private TerminatingNetworkRoutingNumberImpl tnRoutingNumber = null;

	public ForwardGVNSImpl(OriginatingParticipatingServiceProviderImpl opServiceProvider, GVNSUserGroupImpl gvnsUserGroup, TerminatingNetworkRoutingNumberImpl tnRoutingNumber) {
		super();
		this.opServiceProvider = opServiceProvider;
		this.gvnsUserGroup = gvnsUserGroup;
		this.tnRoutingNumber = tnRoutingNumber;
	}

	public ForwardGVNSImpl(byte[] b) throws ParameterRangeInvalidException {
		super();
		decodeElement(b);
	}
	public ForwardGVNSImpl()  {
		super();

	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.mobicents.isup.ISUPComponent#decodeElement(byte[])
	 */
	public int decodeElement(byte[] b) throws ParameterRangeInvalidException {
		// Add kength ? || b.length != xxx
		if (b == null) {
			throw new ParameterRangeInvalidException("byte[] must  not be null");
		}
		ByteArrayInputStream bis = new ByteArrayInputStream(b);
		this.opServiceProvider = new OriginatingParticipatingServiceProviderImpl();
		this.gvnsUserGroup = new GVNSUserGroupImpl();
		this.tnRoutingNumber = new TerminatingNetworkRoutingNumberImpl();

		int count = 0;
		count += this.opServiceProvider.decodeElement(bis);
		count += this.gvnsUserGroup.decodeElement(bis);
		count += this.tnRoutingNumber.decodeElement(bis);

		return count;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.mobicents.isup.ISUPComponent#encodeElement()
	 */
	public byte[] encodeElement() throws IOException {

		if (this.opServiceProvider == null) {
			throw new IllegalArgumentException("OriginatingParticipatingServiceProvider must not be null.");
		}
		if (this.gvnsUserGroup == null) {
			throw new IllegalArgumentException("GVNSUserGruop must not be null.");
		}
		if (this.tnRoutingNumber == null) {
			throw new IllegalArgumentException("TerminatingNetworkRoutingNumber must not be null.");
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write(this.opServiceProvider.encodeElement());
		bos.write(this.gvnsUserGroup.encodeElement());
		bos.write(this.tnRoutingNumber.encodeElement());
		return bos.toByteArray();
	}

	public OriginatingParticipatingServiceProvider getOpServiceProvider() {
		return opServiceProvider;
	}

	public void setOpServiceProvider(OriginatingParticipatingServiceProvider opServiceProvider) {
		this.opServiceProvider = (OriginatingParticipatingServiceProviderImpl) opServiceProvider;
	}

	public GVNSUserGroup getGvnsUserGroup() {
		return gvnsUserGroup;
	}

	public void setGvnsUserGroup(GVNSUserGroup gvnsUserGroup) {
		this.gvnsUserGroup = (GVNSUserGroupImpl) gvnsUserGroup;
	}

	public TerminatingNetworkRoutingNumber getTnRoutingNumber() {
		return tnRoutingNumber;
	}

	public void setTnRoutingNumber(TerminatingNetworkRoutingNumber tnRoutingNumber) {
		this.tnRoutingNumber = (TerminatingNetworkRoutingNumberImpl) tnRoutingNumber;
	}

	public int getCode() {

		return _PARAMETER_CODE;
	}
}