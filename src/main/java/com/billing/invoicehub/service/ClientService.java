package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.ClientDto;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Client;
import com.billing.invoicehub.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public List<ClientDto> findAll() {
        return clientRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<ClientDto> findByOwnerId(Long ownerId) {
        return clientRepository.findByOwner_Id(ownerId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Optional<ClientDto> findById(Long id) {
        return clientRepository.findById(id).map(this::convertToDto);
    }

    public Optional<Client> findEntityById(Long id) {
        return clientRepository.findById(id);
    }

    public Optional<Client> findByCompanyNameIgnoreCaseAndOwnerId(String companyName, Long ownerId) {
        return clientRepository.findByCompanyNameIgnoreCaseAndOwner_Id(companyName, ownerId);
    }

    public Optional<Client> findByCompanyNameIgnoreCase(String companyName) {
        return clientRepository.findAllByCompanyNameIgnoreCase(companyName).stream().findFirst();
    }

    @Transactional
    public ClientDto save(ClientDto dto, AppUser owner) {
        Client client = new Client();
        updateEntityFromDto(client, dto);
        client.setOwner(owner);
        Client saved = clientRepository.save(client);
        return convertToDto(saved);
    }

    @Transactional
    public Client saveEntity(Client client) {
        return clientRepository.save(client);
    }

    @Transactional
    public Optional<ClientDto> update(Long id, ClientDto dto) {
        return clientRepository.findById(id).map(client -> {
            updateEntityFromDto(client, dto);
            Client saved = clientRepository.save(client);
            return convertToDto(saved);
        });
    }

    @Transactional
    public boolean delete(Long id) {
        if (clientRepository.existsById(id)) {
            clientRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // Manual Mapping Helpers
    public ClientDto convertToDto(Client client) {
        if (client == null) {
            return null;
        }
        ClientDto dto = new ClientDto();
        dto.setId(client.getId());
        dto.setCompanyName(client.getCompanyName());
        dto.setGstNumber(client.getGstNumber());
        dto.setEmail(client.getEmail());
        dto.setPhone(client.getPhone());
        dto.setAddress(client.getAddress());
        return dto;
    }

    public void updateEntityFromDto(Client client, ClientDto dto) {
        if (client == null || dto == null) {
            return;
        }
        client.setCompanyName(dto.getCompanyName());
        client.setGstNumber(dto.getGstNumber());
        client.setEmail(dto.getEmail());
        client.setPhone(dto.getPhone());
        client.setAddress(dto.getAddress());
    }
}
