package com.bancario.msdirectorio.service;

import com.bancario.msdirectorio.model.Institucion;
import com.bancario.msdirectorio.model.InterruptorCircuito;
import com.bancario.msdirectorio.model.ReglaEnrutamiento;
import com.bancario.msdirectorio.repository.InstitucionRepository;
import com.bancario.msdirectorio.repository.InterruptorRepository;
import com.bancario.msdirectorio.repository.ReglaEnrutamientoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class DirectorioService {

    @Autowired
    private InstitucionRepository institucionRepository;

    @Autowired
    private ReglaEnrutamientoRepository reglaRepository;

    @Autowired
    private InterruptorRepository interruptorRepository; // <--- NUEVO

    // --- Lógica para Instituciones ---

    @Transactional
    public Institucion registrarInstitucion(Institucion institucion) {
        if (institucionRepository.existsByCodigoBic(institucion.getCodigoBic())) {
            throw new RuntimeException("El banco con BIC " + institucion.getCodigoBic() + " ya existe.");
        }

        // 1. Guardamos la Institución
        Institucion guardada = institucionRepository.save(institucion);

        
        InterruptorCircuito interruptor = new InterruptorCircuito();
        
        interruptor.setInstitucion(guardada);

        interruptor.setFallosConsecutivos(0);
        interruptor.setEstaAbierto(false);
        interruptor.setUltimoFallo(null);

        interruptorRepository.save(interruptor);
        return guardada;
    }

    public List<Institucion> listarTodas() {
        return institucionRepository.findAll();
    }

    public Optional<Institucion> buscarPorBic(String bic) {
        // 1. Validamos primero si el banco está "castigado" (Circuit Breaker)
        if (!validarDisponibilidad(bic)) {
            // Opción Arquitectónica: Devolver vacío para simular "Caída de servicio"
            // Esto hará que el Controller devuelva 404 Not Found
            System.out.println(">>> CIRCUIT BREAKER ACTIVADO: Bloqueando acceso a " + bic);
            return Optional.empty(); 
        }

        // 2. Si está sano, lo buscamos
        return institucionRepository.findById(bic);
    }

    

    @Transactional
    public ReglaEnrutamiento registrarRegla(ReglaEnrutamiento regla) {
        String bic = regla.getInstitucion().getCodigoBic();
        Institucion banco = institucionRepository.findById(bic)
                .orElseThrow(() -> new RuntimeException("Banco no encontrado: " + bic));

        regla.setInstitucion(banco);
        return reglaRepository.save(regla);
    }

    

    public Optional<Institucion> descubrirBancoPorBin(String bin) {
        Optional<ReglaEnrutamiento> regla = reglaRepository.findByPrefijoBin(bin);

        if (regla.isPresent()) {
            Institucion banco = regla.get().getInstitucion();

            // VALIDACIÓN DE SEGURIDAD: ¿El banco está bloqueado por el Circuit Breaker?
            if (!validarDisponibilidad(banco.getCodigoBic())) {
                // Lanzar excepción. Por ahora, devolvemos empty para proteger el sistema.
                System.out.println("ADVERTENCIA: Intento de ruta hacia " + banco.getCodigoBic() + " bloqueado por Circuit Breaker.");
                return Optional.empty();
            }
            return Optional.of(banco);
        }
        return Optional.empty();
    }

    // --- MÉTODOS PRIVADOS DEL CIRCUIT BREAKER ---

    private boolean validarDisponibilidad(String bic) {
        InterruptorCircuito interruptor = interruptorRepository.findById(bic).orElse(null);
        if (interruptor == null) return true; // Si no hay registro, asumimos que está bien

        if (interruptor.getEstaAbierto()) {
            // Si está abierto, verificamos si ya pasó el tiempo de castigo (ej. 60 seg)
            if (interruptor.getUltimoFallo() != null) {
                long segundos = ChronoUnit.SECONDS.between(interruptor.getUltimoFallo(), LocalDateTime.now());
                if (segundos > 60) {
                    return true; // Half-Open: Permitimos probar de nuevo
                }
            }
            return false; 
        }
        return true; 
    }
}