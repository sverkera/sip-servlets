package org.mobicents.slee.resource.sip11.wrappers;

import gov.nist.javax.sip.ListeningPointImpl;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.SIPRequest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogDoesNotExistException;
import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.Transaction;
import javax.sip.TransactionDoesNotExistException;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.AddressPlan;
import javax.slee.facilities.Tracer;

import net.java.slee.resource.sip.DialogActivity;

import org.mobicents.slee.resource.sip11.DialogWithIdActivityHandle;
import org.mobicents.slee.resource.sip11.SipActivityHandle;
import org.mobicents.slee.resource.sip11.SipResourceAdaptor;
import org.mobicents.slee.resource.sip11.SleeSipProviderImpl;
import org.mobicents.slee.resource.sip11.Utils;

/**
 * Abstract dialog wrapper code.
 * 
 * @author martins
 *
 */
public class DialogWrapper extends Wrapper implements DialogActivity {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * 
	 */
	protected transient ConcurrentHashMap<SipActivityHandle, ClientTransactionWrapper> ongoingClientTransactions = new ConcurrentHashMap<SipActivityHandle, ClientTransactionWrapper>(1);
	
	/**
	 * 
	 */
	protected transient ConcurrentHashMap<SipActivityHandle, ServerTransactionWrapper> ongoingServerTransactions = new ConcurrentHashMap<SipActivityHandle, ServerTransactionWrapper>(1);
	
	/**
	 * the wrapped dialog
	 */
	protected transient Dialog wrappedDialog;
	
	/**
	 * the local tag of the dialog, for certain cases it is out of sync with wrapped dialog, because the wrapped dialog may be created without one assigned 
	 */
	private String localTag;
	
	/*
	 * the slee {@link Address} where events on this dialog are fired.
	 */
	protected transient javax.slee.Address eventFiringAddress;
	
	/**
	 * tracer for this class
	 */
	private static Tracer tracer;

	/**
	 * 
	 */
	protected SipActivityHandle lastCancelableTransactionId;

	/**
	 * used to delay the dialog .delete() when there are ongoing client txs
	 */
	private transient boolean pendingDelete = false;

	// Constructors 

	/**
	 * 
	 * @param wrappedDialog
	 * @param dialogId
	 * @param localTag
	 * @param ra
	 * @throws IllegalArgumentException
	 */
	public DialogWrapper(Dialog wrappedDialog, String dialogId, String localTag, SipResourceAdaptor ra) throws IllegalArgumentException {
		this(new DialogWithIdActivityHandle(dialogId),localTag,ra);
		this.wrappedDialog = wrappedDialog;
		this.wrappedDialog.setApplicationData(this);
	}
	
	/**
	 * Extensible constructor.
	 * @param sipActivityHandle
	 * @param localTag
	 * @param ra
	 */
	protected DialogWrapper(SipActivityHandle sipActivityHandle, String localTag,SipResourceAdaptor ra) {
		super(sipActivityHandle);
		setResourceAdaptor(ra);
		this.localTag = localTag;
	}		
	
	@Override
	public void setResourceAdaptor(SipResourceAdaptor ra) {
		super.setResourceAdaptor(ra);
		if (tracer == null) {
			tracer = ra.getTracer(DialogWrapper.class.getSimpleName());
		}
	}
	
	// Wrapper Methods

	@Override
	public javax.slee.Address getEventFiringAddress() {
		if (eventFiringAddress == null) {
			eventFiringAddress = new javax.slee.Address(AddressPlan.SIP, wrappedDialog.getLocalParty().toString());			
		}
		return eventFiringAddress;
	}	

	@Override
	public boolean isAckTransaction() {
		return false;
	}
	
	@Override
	public boolean isActivity() {
		return true;
	}
	
	@Override
	public boolean isDialog() {
		return true;
	}
	
	// Dialog Activity Methods
	
	/*
	 * (non-Javadoc)
	 * @see net.java.slee.resource.sip.DialogActivity#associateServerTransaction(javax.sip.ClientTransaction, javax.sip.ServerTransaction)
	 */
	public void associateServerTransaction(ClientTransaction ct, ServerTransaction st) {
		if (ct == null) {
			throw new NullPointerException("null client transaction");
		}		
		if (st == null) {
			throw new NullPointerException("null server transaction");
		}
		final ClientTransactionWrapper ctw = (ClientTransactionWrapper) ct;
		final ServerTransactionWrapper stw = (ServerTransactionWrapper) st;
		
		final DialogWrapper stDialog = (DialogWrapper) st.getDialog();
		if (stDialog == null) {
			throw new IllegalArgumentException("the specified server transaction has no dialog.");
		}					
		ctw.associateServerTransaction(stw, stDialog.getActivityHandle());
	}

	/*
	 * (non-Javadoc)
	 * @see net.java.slee.resource.sip.DialogActivity#getAssociatedServerTransaction(javax.sip.ClientTransaction)
	 */
	public ServerTransaction getAssociatedServerTransaction(ClientTransaction ct) {
		
		if(ct == null) {
			throw new NullPointerException("Passed Client Transaction is null!");
		}
		
		final ClientTransactionWrapper ctw = (ClientTransactionWrapper) ct;
		final ClientTransactionAssociation cta = ctw.getClientTransactionAssociation();
		if (cta != null) {
			final DialogWrapper associatedDialog = (DialogWrapper) ra.getActivity(cta.getDialogActivityHandle());
			if (associatedDialog != null) {
				return associatedDialog.getServerTransaction(cta.getAssociatedServerTransaction());
			}
			else {
				return null;
			}
		}
		else {
			return null;
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#createRequest(java.lang.String)
	 */
	public Request createRequest(String methodName) throws SipException {
		try{
			final Request request = this.wrappedDialog.createRequest(methodName);
			if (getState() == null) {
				// adds load balancer to route if exists
				ra.getProviderWrapper().addLoadBalancerToRoute(request);
			}
			return request;
		}catch (Exception e) {
			throw new SipException(e.getMessage(),e);
		}
	}
	
	/*
	 * 
	 * @see
	 * net.java.slee.resource.sip.DialogActivity#createRequest(javax.sip.message
	 * .Request)
	 */
	@SuppressWarnings("unchecked")
	public Request createRequest(Request origRequest) throws SipException {

		final SleeSipProviderImpl provider = ra.getProviderWrapper();
		
		final SIPRequest request = (SIPRequest) origRequest.clone();

		// note: no need to work on dialog tags, since remote tag remains the same and
		// local tag will be ensured when sending

		/*
		 * If the B2BUA decides to relay the received request, its associated
		 * UAC generates a new downstream SIP request with its new Via, Max-
		 * Forwards, Call-ID, CSeq, and Contact header fields, as described in
		 * RFC3261.
		 */
		final String transport = request.getTopmostViaHeader().getTransport();
		final ListeningPointImpl listeningPointImpl = (ListeningPointImpl) provider.getListeningPoint(transport);


		final ViaList viaList = new ViaList();
		viaList.add((Via) listeningPointImpl.createViaHeader());
		request.setVia(viaList);
		
		try {
			request.setHeader(provider.getHeaderFactory().createMaxForwardsHeader(70));
		} catch (InvalidArgumentException e) {
			throw new SipException("Failed to create max forwards header",e);
		}
		request.setHeader((Header) getCallId().clone());
		// note: cseq will be set by dialog when sending
		// set contact if the original response had it
		if (origRequest.getHeader(ContactHeader.NAME) != null) {
			request.setHeader(listeningPointImpl.createContactHeader());
		}
		
		/*
		 * Route header fields of the upstream request MAY be copied in the
		 * downstream request, except the topmost Route header if it is under
		 * the responsibility of the B2BUA. Additional Route header fields MAY
		 * also be added to the downstream request.
		 */
		if (getState() == null) {
			// first request, no route available
			final RouteList routeList = request.getRouteHeaders();
			if (routeList != null) {
				final RouteHeader topRoute = routeList.get(0);
				final URI topRouteURI = topRoute.getAddress().getURI();
				if (topRouteURI.isSipURI()) {
					final SipURI topRouteSipURI = (SipURI) topRouteURI;
					if (topRouteSipURI.getHost().equals(listeningPointImpl.getIPAddress())
							&& topRouteSipURI.getPort() == listeningPointImpl.getPort()) {
						if (routeList.size() > 1) {
							routeList.remove(0);
						}
						else {
							request.removeHeader(RouteHeader.NAME);
						}					
					}
				}
			}
			// adds load balancer to route if exists
			ra.getProviderWrapper().addLoadBalancerToRoute(request);
		}
		else {
			// replace route in orig request with the one in dialog
			request.removeHeader(RouteHeader.NAME);
			final RouteList routeList = new RouteList();
			for (Iterator<Route> it = wrappedDialog.getRouteSet(); it.hasNext();) {
				Route route = it.next();				
				routeList.add(route);								
			}
			if (!routeList.isEmpty()) {
				request.addHeader(routeList);
			}
		}
		
		/*
		 * Record-Route header fields of the upstream request are not copied in
		 * the new downstream request, as Record-Route is only meaningful for
		 * the upstream dialog.
		 */
		request.removeHeader(RecordRouteHeader.NAME);

		return request;		
	}
	
	/*
	 * (non-Javadoc)
	 * @see net.java.slee.resource.sip.DialogActivity#sendRequest(javax.sip.message.Request)
	 */
	public ClientTransaction sendRequest(Request request) throws SipException, TransactionUnavailableException {
		ensureCorrectDialogLocalTag(request);
		final ClientTransactionWrapper ctw = ra.getProviderWrapper().getNewDialogActivityClientTransaction(this,request);
		if (request.getMethod().equals(Request.INVITE))
			lastCancelableTransactionId = ctw.getActivityHandle();
		if (tracer.isInfoEnabled()) {
			tracer.info(String.valueOf(ctw)+" sending request:\n"+request);
		}
		wrappedDialog.sendRequest(ctw.getWrappedClientTransaction());				
		return ctw;
	}
	
	/**
	 * 
	 * @param request
	 * @throws SipException
	 */
	protected void ensureCorrectDialogLocalTag(Request request) throws SipException {
		if (localTag != null) {
			// ensure we are using the right tag
			try {
				((FromHeader)request.getHeader(FromHeader.NAME)).setTag(localTag);
			} catch (ParseException e) {
				throw new SipException(e.getMessage(),e);
			}
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#sendRequest(javax.sip.ClientTransaction)
	 */
	public void sendRequest(ClientTransaction ct) throws TransactionDoesNotExistException, SipException {
		final Request request = ct.getRequest();
		ensureCorrectDialogLocalTag(request);
		if (tracer.isInfoEnabled()) {
			tracer.info(String.valueOf(ct)+" sending request:\n"+request);
		}
		wrappedDialog.sendRequest(((ClientTransactionWrapper)ct).getWrappedClientTransaction());		
	}
	
	/*
	 * FIXME untested
	 * (non-Javadoc)
	 * @see net.java.slee.resource.sip.DialogActivity#createResponse(javax.sip.ServerTransaction, javax.sip.message.Response)
	 */
	@SuppressWarnings("unchecked")
	public Response createResponse(ServerTransaction origServerTransaction, Response receivedResponse) throws SipException {

		final SleeSipProviderImpl provider = ra.getProviderWrapper();
		
		Response forgedResponse = null;
		
		try {
			forgedResponse = provider.getMessageFactory().createResponse(receivedResponse.getStatusCode(), origServerTransaction.getRequest());
		} catch (ParseException e) {
			throw new SipException("Failed to forge message", e);
		}
		
		final DialogState dialogState = getState();
		final String localTag = getLocalTag();
		if ((dialogState == null || dialogState == DialogState.EARLY) && localTag != null && isServer()) {
			// no tag set in the response, since the dialog creating transaction didn't had it
			try {
				((ToHeader)forgedResponse.getHeader(ToHeader.NAME)).setTag(localTag);
			} catch (ParseException e) {
				throw new SipException("Failed to set local tag", e);
			}
		}
		
		// copy headers 
		ListIterator<String> lit = receivedResponse.getHeaderNames();
		String headerName = null;
		ListIterator<Header> headersIterator = null;
		while (lit.hasNext()) {
			headerName = lit.next();
			if (Utils.getHeadersToOmmitOnResponseCopy().contains(headerName)) {
				continue;
			} else {
				forgedResponse.removeHeader(headerName);
				headersIterator = receivedResponse.getHeaders(headerName);
				while (headersIterator.hasNext()) {
					forgedResponse.addLast((Header)headersIterator.next().clone());
				}
			}
		}
		
		// Copy content
		final byte[] rawOriginal = receivedResponse.getRawContent();
		if (rawOriginal != null && rawOriginal.length != 0) {
			final byte[] copy = new byte[rawOriginal.length];
			System.arraycopy(rawOriginal, 0, copy, 0, copy.length);
			try {
				forgedResponse.setContent(copy, (ContentTypeHeader) forgedResponse
						.getHeader(ContentTypeHeader.NAME));
			} catch (ParseException e) {
				tracer.severe("Failed to set content on forged response. To copy value [" + new String(copy) + "] Type ["
						+ receivedResponse.getHeader(ContentTypeHeader.NAME) + "]\n", e);
			}
		}
		
		// set contact if the received response had it
		if (receivedResponse.getHeader(ContactHeader.NAME) != null) {
			final String transport = ((ViaHeader) forgedResponse.getHeader(ViaHeader.NAME)).getTransport();
			forgedResponse.setHeader(((ListeningPointImpl)provider.getListeningPoint(transport)).createContactHeader());
		}
		
		return forgedResponse;
		
	}

	/*
	 * (non-Javadoc)
	 * @see net.java.slee.resource.sip.DialogActivity#sendCancel()
	 */
	public ClientTransaction sendCancel() throws SipException {
		try {
			final ClientTransaction inviteCTX = this.getClientTransaction(lastCancelableTransactionId);
			final ClientTransactionWrapper cancelTransaction = ra.getProviderWrapper().getNewDialogActivityClientTransaction(this,inviteCTX.createCancel());
			if (tracer.isInfoEnabled()) {
				tracer.info(String.valueOf(cancelTransaction) + " sending request:\n"
						+ cancelTransaction.getRequest());
			}
			cancelTransaction.getWrappedClientTransaction().sendRequest();
			return cancelTransaction;
		} catch (NullPointerException npe) {
			throw new SipException("Failed to find client transaction or no INVITE transaction present",npe);
		} catch (Exception e) {
			throw new SipException("Failed to send CANCEL due to:", e);
		}
	}
	
	// Dialog Methods
	
	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#createAck(long)
	 */
	public Request createAck(long arg0) throws InvalidArgumentException, SipException {
		return wrappedDialog.createAck(arg0);
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#createPrack(javax.sip.message.Response)
	 */
	public Request createPrack(Response arg0) throws DialogDoesNotExistException, SipException {
		return this.wrappedDialog.createPrack(arg0);
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#createReliableProvisionalResponse(int)
	 */
	public Response createReliableProvisionalResponse(int arg0) throws InvalidArgumentException, SipException {
		return wrappedDialog.createReliableProvisionalResponse(arg0);		
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#delete()
	 */
	public void delete() {
		
		if (pendingDelete = (ongoingClientTransactions != null && !ongoingClientTransactions.isEmpty())) {
			// ongoing client txs, need those to end first
			return;
		}
		
		final DialogState currentState = wrappedDialog != null ? wrappedDialog.getState() : null;
		boolean stackDoesNotFiresDialogTerminatedEvent = !isEnding()
			&& !wrappedDialog.isServer() && (currentState == null || currentState == DialogState.TERMINATED);
		wrappedDialog.delete();
		if (stackDoesNotFiresDialogTerminatedEvent) {
			ra.processDialogTerminated(this);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getCallId()
	 */
	public CallIdHeader getCallId() {
		return this.wrappedDialog.getCallId();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getDialogId()
	 */
	public String getDialogId() {
		// FIXME if localtag is set it is not good
		return this.wrappedDialog.getDialogId();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getFirstTransaction()
	 */
	@Deprecated
	public Transaction getFirstTransaction() {
		return this.wrappedDialog.getFirstTransaction();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getLocalParty()
	 */
	public Address getLocalParty() {
		return this.wrappedDialog.getLocalParty();
	}
	
	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getRemoteParty()
	 */
	public Address getRemoteParty() {
		return this.wrappedDialog.getRemoteParty();
	}
	
	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getRemoteTarget()
	 */
	public Address getRemoteTarget() {
		return this.wrappedDialog.getRemoteTarget();
	}
	
	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getLocalSeqNumber()
	 */
	public long getLocalSeqNumber() {
		return this.wrappedDialog.getLocalSeqNumber();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getLocalSequenceNumber()
	 */
	@Deprecated
	public int getLocalSequenceNumber() {
		return this.wrappedDialog.getLocalSequenceNumber();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getLocalTag()
	 */
	public String getLocalTag() {
		if (localTag == null) {
			if (wrappedDialog != null) {
				localTag = wrappedDialog.getLocalTag();
			}
		}
		return localTag;
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getRemoteSeqNumber()
	 */
	public long getRemoteSeqNumber() {
		return this.wrappedDialog.getRemoteSeqNumber();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getRemoteSequenceNumber()
	 */
	@Deprecated
	public int getRemoteSequenceNumber() {
		return this.wrappedDialog.getRemoteSequenceNumber();		
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getRemoteTag()
	 */
	public String getRemoteTag() {
		return this.wrappedDialog.getRemoteTag();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getRouteSet()
	 */
	@SuppressWarnings("unchecked")
	public Iterator<RouteHeader> getRouteSet() {
		return this.wrappedDialog.getRouteSet();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#getState()
	 */
	public DialogState getState() {
		return this.wrappedDialog.getState();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#incrementLocalSequenceNumber()
	 */
	public void incrementLocalSequenceNumber() {
		this.wrappedDialog.incrementLocalSequenceNumber();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#isSecure()
	 */
	public boolean isSecure() {
		return wrappedDialog.isSecure();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#isServer()
	 */
	public boolean isServer() {
		return wrappedDialog.isServer();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#sendAck(javax.sip.message.Request)
	 */
	public void sendAck(Request arg0) throws SipException {
		if (tracer.isInfoEnabled()) {
			tracer.info(this+" sending ACK:\n"+arg0);
		}
		wrappedDialog.sendAck(arg0);
	}

	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#sendReliableProvisionalResponse(javax.sip.message.Response)
	 */
	public void sendReliableProvisionalResponse(Response arg0) throws SipException {
		wrappedDialog.sendReliableProvisionalResponse(arg0);		
	}
	
	/*
	 * (non-Javadoc)
	 * @see javax.sip.Dialog#terminateOnBye(boolean)
	 */
	public void terminateOnBye(boolean arg0) throws SipException {
		wrappedDialog.terminateOnBye(arg0);
	}
	
	// ...  

	@Override
	public String toString() {
		return new StringBuilder("DialogWrapper Id[").append(this.getDialogId())
			.append("] Handle[").append(this.getActivityHandle())
			.append("] State[").append(this.getState())
			.append("] OngoingCTX[").append(this.ongoingClientTransactions.keySet())
			.append("] OngoingSTX[").append(this.ongoingServerTransactions.keySet()).append("]")
			.toString();
	}

	// Own Methods

	/**
	 * 
	 * @param respEvent
	 * @return <ul>
	 *         <li><b>true</b> - if DialogWrapper finished processing and RA
	 *         doesnt have to take any action</li>
	 *         <li><b>false</b> - DialogWrapper did nothing, ra should triger
	 *         regular logic</li>
	 *         </ul>
	 */
	public boolean processIncomingResponse(ResponseEvent respEvent) {
		return false;
	}
	
	/**
	 * 
	 * @param transaction
	 * @return
	 */
	public ClientTransaction getClientTransaction(SipActivityHandle transaction) {
		return this.ongoingClientTransactions.get(transaction);
	}

	/**
	 * 
	 * @param transaction
	 * @return
	 */
	public ServerTransaction getServerTransaction(SipActivityHandle transaction) {
		return this.ongoingServerTransactions.get(transaction);
	}
	
	/**
	 * 
	 * @param stw
	 */
	public boolean addOngoingTransaction(ServerTransactionWrapper stw) {
		final boolean alreadyMapped = ongoingServerTransactions.put(stw.getActivityHandle(), stw) == null;
		// not needed till we have some sort of tx replication
		// updateReplicatedState();
		return alreadyMapped;
	}

	/**
	 * For now we will do like this to force replication in jsip ha, but it is probably better to use the ra replicated data
	 */
	protected void updateReplicatedState() {
		if (!ra.inLocalMode()) {
			wrappedDialog.setApplicationData(this);
		}
	}
	
	/**
	 * 
	 * @param ctw
	 */
	public boolean addOngoingTransaction(ClientTransactionWrapper ctw) {
		final boolean alreadyMapped = ongoingClientTransactions.put(ctw.getActivityHandle(), ctw) == null;
		// not needed till we have some sort of tx replication
		// updateReplicatedState();
		return alreadyMapped;
	}

	/**
	 * 
	 * @param ctw
	 */
	public void removeOngoingTransaction(ClientTransactionWrapper ctw) {
		if (ongoingClientTransactions != null) {
			if (ongoingClientTransactions.remove(ctw.getActivityHandle()) != null) {
				if (pendingDelete) {
					delete();
				}
				// not needed till we have some sort of tx replication
				// updateReplicatedState();
			}
		}
	}

	/**
	 * 
	 * @param stw
	 */
	public void removeOngoingTransaction(ServerTransactionWrapper stw) {
		if (ongoingServerTransactions != null) {
			if (ongoingServerTransactions.remove(stw.getActivityHandle()) != null) {
				// not needed till we have some sort of tx replication
				// updateReplicatedState();
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.mobicents.slee.resource.sip11.wrappers.Wrapper#clear()
	 */
	@Override
	public void clear() {
		super.clear();
		if (wrappedDialog != null) {
			wrappedDialog.setApplicationData(null);
			wrappedDialog = null;
		}		
		ongoingClientTransactions = null;
		ongoingServerTransactions = null;
		localTag = null;
		lastCancelableTransactionId = null;
		eventFiringAddress = null;
	}
	
	static final String[] EMPTY_STRING_ARRAY = {};
	
	private void writeObject(ObjectOutputStream stream) throws IOException {
		
		// write everything not static or transient
		stream.defaultWriteObject();
		
		// TODO work this out once we have some sort of tx replication
		// now write the ongoing client and server txs
		//final String[] ctIds = ongoingClientTransactions.keySet().toArray(EMPTY_STRING_ARRAY);
		//stream.writeObject(ctIds);
		//final String[] stIds = ongoingServerTransactions.keySet().toArray(EMPTY_STRING_ARRAY);
		//stream.writeObject(stIds);
	}
	
	private void readObject(ObjectInputStream stream)  throws IOException, ClassNotFoundException {
				
		stream.defaultReadObject();

		// TODO work this out once we have some sort of tx replication
		//final String[] ctIds = (String[]) stream.readObject();
		//final String[] stIds = (String[]) stream.readObject();
		// re-populate the ongoing server and client tx maps fetching txs from stack
		// TODO
		ongoingClientTransactions = new ConcurrentHashMap<SipActivityHandle, ClientTransactionWrapper>(1);
		ongoingServerTransactions = new ConcurrentHashMap<SipActivityHandle, ServerTransactionWrapper>(1);		
		activityHandle.setActivity(this);
	}
	
	/**
	 * @return the wrappedDialog
	 */
	public Dialog getWrappedDialog() {
		return wrappedDialog;
	}
	
	/**
	 * @param wrappedDialog the wrappedDialog to set
	 */
	public void setWrappedDialog(Dialog wrappedDialog) {
		this.wrappedDialog = wrappedDialog;
	}
	
}