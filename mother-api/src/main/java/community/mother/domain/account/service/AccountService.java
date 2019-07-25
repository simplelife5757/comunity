package community.mother.domain.account.service;

import community.mother.domain.account.domain.Account;
import community.mother.domain.account.domain.AccountAdapter;
import community.mother.domain.account.domain.AccountRepository;
import community.mother.domain.account.domain.AccountRole;
import community.mother.domain.account.dto.request.LoginAccountParams;
import community.mother.domain.account.dto.request.SaveAccountParams;
import community.mother.domain.account.dto.request.UpdateAccountParams;
import community.mother.domain.account.dto.response.AccountDetail;
import community.mother.domain.account.dto.response.AccountListResponse;
import community.mother.domain.account.exception.AccountNotFoundException;
import community.mother.domain.account.exception.EmailNotFoundException;
import community.mother.domain.account.exception.PasswordMismatchException;
import community.mother.domain.model.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService implements UserDetailsService {
	private final AccountRepository accountRepository;
	private final PasswordEncoder passwordEncoder;

	public AccountListResponse getAccounts() {
		return  new AccountListResponse(accountRepository.findAll().stream()
										.map(account -> new AccountDetail(account.getEmail(), account.getNickname()))
										.collect(Collectors.toList()));
	}

	public Long createAccount(SaveAccountParams accountParams) {
		String encodedPassword = passwordEncoder.encode(accountParams.getPassword());
		Set<AccountRole> roles = getRoles(accountParams);

		Account account = Account.builder()
				.email(accountParams.getEmail())
				.nickname(accountParams.getNickname())
				.username(accountParams.getUsername())
				.password(encodedPassword)
				.roles(roles)
				.build();
		return accountRepository.save(account).getId();
	}

	private Set<AccountRole> getRoles(SaveAccountParams accountParams) {
		if (accountParams.getRoles().isEmpty()) {
			return Set.of(AccountRole.USER);
		}
		return accountParams.getRoles();
	}

	public Long login(LoginAccountParams accountParams, HttpSession session) {
		Email email = accountParams.getEmail();
		Account account = accountRepository.findByEmail(email).orElseThrow(EmailNotFoundException::new);
		if (account.matchPassword(accountParams.getPassword(), passwordEncoder)) {
			log.info("login success! accountParams={}", accountParams);
			session.setAttribute("LOGIN_ACCOUNT", account);
			return account.getId();
		}
		throw new PasswordMismatchException();
	}

	public void updateAccount(Long id, UpdateAccountParams accountParams) {
		Account account = findAccountById(id);
		account.update(accountParams.getUsername(), accountParams.getNickname(), accountParams.getWebsite(), accountParams.getDescription()
				, accountParams.getEmail(), accountParams.getPhone(), accountParams.getGender());
		accountRepository.save(account);
	}

	public void deleteAccount(Long id) {
		Account account = findAccountById(id);
		account.delete();
		accountRepository.save(account);
	}

	private Account findAccountById(Long id) {
		Account account = accountRepository.findById(id).orElseThrow(AccountNotFoundException::new);

		if(account.isDeleted()) {
			throw new AccountNotFoundException();
		}

		return account;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Account account = accountRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException(username));
		return new AccountAdapter(account);
	}
}
