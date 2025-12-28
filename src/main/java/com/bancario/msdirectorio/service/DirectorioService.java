package com.bancario.msdirectorio.service;

import com.bancario.msdirectorio.model.Institucion;
import com.bancario.msdirectorio.model.ReglaEnrutamiento;
import com.bancario.msdirectorio.repository.InstitucionRepository;
import com.bancario.msdirectorio.repository.ReglaEnrutamientoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DirectorioService {

    @Autowired
    private InstitucionRepository institucionRepository;

    @Autowired
    private ReglaEnrutamientoRepository reglaRepository;

    // --- Lógica para Instituciones ---

    @Transactional
    public Institucion registrarInstitucion(Institucion institucion) {
        if (institucionRepository.existsByCodigoBic(institucion.getCodigoBic())) {
            throw new RuntimeException("El banco con BIC " + institucion.getCodigoBic() + " ya existe.");
        }
        return institucionRepository.save(institucion);
    }

    public List<Institucion> listarTodas() {
        return institucionRepository.findAll();
    }

    public Optional<Institucion> buscarPorBic(String bic) {
        return institucionRepository.findById(bic);
    }

    // --- Lógica para Reglas de Enrutamiento ---

    @Transactional
    public ReglaEnrutamiento registrarRegla(ReglaEnrutamiento regla) {
        // Validamos que el banco exista antes de asignarle un BIN
        String bic = regla.getInstitucion().getCodigoBic();
        Institucion banco = institucionRepository.findById(bic)
                .orElseThrow(() -> new RuntimeException("No se puede crear regla. Banco no encontrado: " + bic));

        regla.setInstitucion(banco);
        return reglaRepository.save(regla);
    }

    // EL MÉTODO MÁS IMPORTANTE PARA EL SWITCH (LOOKUP)
    public Optional<Institucion> descubrirBancoPorBin(String bin) {
        return reglaRepository.findByPrefijoBin(bin)
                .map(ReglaEnrutamiento::getInstitucion);
    }
}